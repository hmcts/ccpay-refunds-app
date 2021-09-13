package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTBACK;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;

@Service
@SuppressWarnings({"PMD.PreserveStackTrace", "PMD.ExcessiveImports"})
public class RefundsServiceImpl extends StateUtil implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR0[0-9][0-9]-[a-zA-Z]+)");

    private static final Pattern STATUSPATTERN = Pattern.compile("[^rejected]");

    private static final String OTHERREASONPATTERN = "Other - ";

    private static int reasonPrefixLength = 6;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RejectionReasonRepository rejectionReasonRepository;

    @Autowired
    private ReferenceUtil referenceUtil;

    @Autowired
    private IdamService idamService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RefundReasonRepository refundReasonRepository;

    @Autowired
    private RefundResponseMapper refundResponseMapper;

    @Autowired
    private StatusHistoryResponseMapper statusHistoryResponseMapper;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Override
    public RefundEvent[] retrieveActions(String reference) {
        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        return currentRefundState.nextValidEvents();
    }

    @Override
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException {
        validateRefundRequest(refundRequest);
        String uid = idamService.getUserId(headers);
        Refund refund = initiateRefundEntity(refundRequest, uid);
        refundsRepository.save(refund);
        LOG.info("Refund saved");
        return RefundResponse.buildRefundResponseWith()
            .refundReference(refund.getReference())
            .build();
    }

    @Override
    public RefundListDtoResponse getRefundList(String status, MultiValueMap<String, String> headers,
                                               String ccdCaseNumber, String excludeCurrentUser, List<String> roles) {

        Optional<List<Refund>> refundList = Optional.empty();

        //Return Refund list based on ccdCaseNumber if its not blank
        if (StringUtils.isNotBlank(ccdCaseNumber)) {
            refundList = refundsRepository.findByCcdCaseNumber(ccdCaseNumber);
        } else if (StringUtils.isNotBlank(status)) {
            RefundStatus refundStatus = RefundStatus.getRefundStatus(status.toLowerCase());

            //Get the userId
            String uid = idamService.getUserId(headers);

            //get the refund list except the self uid
            refundList = SENTFORAPPROVAL.getName().equalsIgnoreCase(status) && "true".equalsIgnoreCase(
                    excludeCurrentUser) ? refundsRepository.findByRefundStatusAndCreatedByIsNot(
                    refundStatus,
                    uid
            ) : refundsRepository.findByRefundStatus(refundStatus);
        }

        // Filter Refunds List based on Service Type
        if (null != roles && !roles.isEmpty() && !refundList.isEmpty()) {
            refundList = filterRefundList(refundList, headers, roles);
        }
        return getRefundListDto(headers, refundList);
    }

    private Optional<List<Refund>> filterRefundList(Optional<List<Refund>> optionalRefundList, MultiValueMap<String, String> headers,
                                                    List<String> roles) {
        Set<String> distintUserIDSet = idamService.getUserIdSetForRoles(headers, roles);

        if (optionalRefundList.isPresent()) {
            List<Refund> refundList = optionalRefundList.get();
            refundList = refundList.stream()
                    .filter(refunds -> distintUserIDSet.contains(refunds.getCreatedBy()))
                    .collect(Collectors.toList());
            return Optional.of(refundList);
        }
        throw new RefundListEmptyException("Refund list is empty for given criteria");
    }

    public RefundListDtoResponse getRefundListDto(MultiValueMap<String, String> headers, Optional<List<Refund>> refundList) {

        if (refundList.isPresent() && !refundList.get().isEmpty()) {
            return RefundListDtoResponse
                .buildRefundListWith()
                .refundList(getRefundResponseDtoList(headers, refundList.get()))
                .build();
        } else {
            throw new RefundListEmptyException("Refund list is empty for given criteria");
        }
    }

    public List<RefundDto> getRefundResponseDtoList(MultiValueMap<String, String> headers, List<Refund> refundList) {
        //Distinct createdBy UID
        Set<String> distintUIDSet = refundList
            .stream().map(Refund::getCreatedBy)
            .collect(Collectors.toSet());

        //Map UID -> User full name
        Map<String, UserIdentityDataDto> userFullNameMap = getIdamUserDetails(headers, distintUIDSet);

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();

        //Update the user full name for created by
        refundList
            .forEach(refund -> {
                String reason  = getRefundReason(refund.getReason());
                refundListDto.add(refundResponseMapper.getRefundListDto(
                    refund,
                    userFullNameMap.get(refund.getCreatedBy()),reason
                ));
                     }


            );

        return refundListDto;
    }

    @Override
    public Refund getRefundForReference(String reference) {
        Optional<Refund> refund = refundsRepository.findByReference(reference);
        if (refund.isPresent()) {
            return refund.get();
        }
        throw new RefundNotFoundException("Refunds not found for " + reference);
    }


//    @Override
//    public HttpStatus reSubmitRefund(MultiValueMap<String, String> headers, String refundReference, RefundRequest refundRequest) {
////        Optional<Refund> refund = refundsRepository.findByReference(refundReference);
////        if (refund.isPresent()) {
//
////            String status = refund.get().getRefundStatus().getName();
////            List<String> nextValidEvents = Arrays.asList(RefundState.valueOf(status).nextValidEvents()).stream().map(
////                refundEvent1 -> refundEvent1.toString()).collect(
////                Collectors.toList());
//
////            RefundEvent[] ve = RefundState.valueOf(status).nextValidEvents();
//
////            if (nextValidEvents.contains(RefundEvent.valueOf(status))) {
////              return new ResponseEntity("Invalid refund event entered next valid refund events is/are : " + nextValidEvents, HttpStatus.BAD_REQUEST);
////            }
////
////          request.setState(currentstate.nextState(currentEventFromRequest));
//
////          if(RefundState.valueOf(refund.get().getRefundStatus().getName()).equals())
////            refund.get().setPaymentReference(refundRequest.getPaymentReference());
////            refund.get().setReason(RefundReason.getReasonObject(refundRequest.getRefundReason()).get());
////            refund.get().setReason(RefundReasonCode.valueOf(refundRequest.getRefundReason().getCode()));
////            refund.get().setRefundStatus(SUBMITTED);
//
////        }
//        return HttpStatus.ACCEPTED;
//
//          request.setState(currentstate.nextState(currentEventFromRequest));

//          if(RefundState.valueOf(refund.get().getRefundStatus().getName()).equals())
//            refund.get().setPaymentReference(refundRequest.getPaymentReference());
//            refund.get().setReason(RefundReason.getReasonObject(refundRequest.getRefundReason()).get());
//            refund.get().setReason(RefundReasonCode.valueOf(refundRequest.getRefundReason().getCode()));
//            refund.get().setRefundStatus(SUBMITTED);

//        }
//        return HttpStatus.ACCEPTED;

//    }

    @Override
    public List<RejectionReasonResponse> getRejectedReasons() {
        // Getting names from Rejection Reasons List object
        return rejectionReasonRepository.findAll().stream().map(reason -> RejectionReasonResponse.rejectionReasonWith()
            .code(reason.getCode())
            .name(reason.getName())
            .build()
        )
            .collect(Collectors.toList());
    }

    @Override
    public List<StatusHistoryDto> getStatusHistory(MultiValueMap<String, String> headers, String reference) {
        List<StatusHistory> statusHistories = null;
        if (null != reference) {

            Refund refund = refundsRepository.findByReferenceOrThrow(reference);

            statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund);
        }

        return getStatusHistoryDto(headers, statusHistories);
    }

    @Override
    public ResubmitRefundResponseDto resubmitRefund(String reference, ResubmitRefundRequest request,
                                         MultiValueMap<String, String> headers) {

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);

        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());

        if (currentRefundState.getRefundStatus().equals(SENTBACK)) {

            // Amount Validation
            validateRefundAmount(refund, request, headers);

            // Refund Reason Validation
            if (null != request.getRefundReason()) {
                refund.setReason(validateRefundReason(request.getRefundReason()));
            }

            // Update Status History table
            String userId = idamService.getUserId(headers);
            refund.setUpdatedBy(userId);
            List<StatusHistory> statusHistories = new ArrayList<>(refund.getStatusHistories());
            refund.setUpdatedBy(userId);
            statusHistories.add(StatusHistory.statusHistoryWith()
                    .createdBy(userId)
                    .status(SENTFORAPPROVAL.getName())
                    .notes("Refund initiated")
                    .build());
            refund.setStatusHistories(statusHistories);
            refund.setRefundStatus(SENTFORAPPROVAL);

            // Update Refunds table
            refundsRepository.save(refund);

            return
                    ResubmitRefundResponseDto.buildResubmitRefundResponseDtoWith()
                            .refundReference(refund.getReference())
                            .refundAmount(refund.getAmount()).build();

        }
        throw new ActionNotFoundException("Action not allowed to proceed");

    }

    private Refund validateRefundAmount(Refund refund, ResubmitRefundRequest request,
                                        MultiValueMap<String, String> headers) {
        PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                headers,
                refund.getPaymentReference()
        );
        if (paymentData.getPayments().get(0).getAmount().compareTo(request.getAmount()) < 0) {
            throw new InvalidRefundRequestException("Amount should not be more than Payment amount");
        }
        refund.setAmount(request.getAmount());
        return refund;
    }

    private String validateRefundReason(String reason) {
        Boolean matcher = REASONPATTERN.matcher(reason).find();
        if (matcher) {
            String reasonCode = reason.split("-")[0];
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reasonCode);
            if(refundReason.getName().startsWith(OTHERREASONPATTERN)){
                return refundReason.getName().split(OTHERREASONPATTERN)[1]+"-"+reason.substring(reasonPrefixLength);
            } else {
                throw new InvalidRefundRequestException("Invalid reason selected");
            }

        } else {
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reason);
            if(refundReason.getName().startsWith(OTHERREASONPATTERN)){
                throw new InvalidRefundRequestException("reason required");
            }
            return refundReason.getCode();
        }
    }

    private List<StatusHistoryDto> getStatusHistoryDto(MultiValueMap<String, String> headers, List<StatusHistory> statusHistories) {

        List<StatusHistoryDto> statusHistoryDtos = new ArrayList<>();

        if (null != statusHistories && !statusHistories.isEmpty()) {
            //Distinct createdBy UID
            Set<String> distintUIDSet = statusHistories
                .stream().map(StatusHistory::getCreatedBy)
                .collect(Collectors.toSet());

            //Map UID -> User full name
            Map<String, UserIdentityDataDto> userFullNameMap = getIdamUserDetails(headers, distintUIDSet);

            statusHistories
                .forEach(statusHistory ->
                             statusHistoryDtos.add(statusHistoryResponseMapper.getStatusHistoryDto(
                                 statusHistory,
                                 userFullNameMap.get(statusHistory.getCreatedBy())
                             )));
        }
        return statusHistoryDtos;
    }

    private Map<String, UserIdentityDataDto> getIdamUserDetails(MultiValueMap<String, String> headers, Set<String> distintUIDSet) {
        Map<String, UserIdentityDataDto> userFullNameMap = new ConcurrentHashMap<>();
        distintUIDSet.forEach(userId -> userFullNameMap.put(
            userId,
            idamService.getUserIdentityData(headers, userId)
        ));
        return userFullNameMap;
    }

    private void validateRefundRequest(RefundRequest refundRequest) {

//        if (isRefundEligibilityFlagged()) {
//            throw new InvalidRefundRequestException("Refund Eligibility flag is unflagged");
//        }

        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(refundRequest.getPaymentReference());
        long refundProcessingCount = refundsList.isPresent() ? refundsList.get().stream().map(refund -> STATUSPATTERN.matcher(
            refund.getRefundStatus().getName()).find()).count() : 0;

        if (refundProcessingCount > 0) {
            throw new InvalidRefundRequestException("Refund is already processed for this payment");
        }

        refundRequest.setRefundReason(validateRefundReason(refundRequest.getRefundReason()));

    }

//
//    private boolean isRefundEligibilityFlagged() {
//        // Actual logic is coming
//        return false;
//    }

    private Refund initiateRefundEntity(RefundRequest refundRequest, String uid) throws CheckDigitException {
        return Refund.refundsWith()
            .amount(refundRequest.getRefundAmount())
            .ccdCaseNumber(refundRequest.getCcdCaseNumber())
            .paymentReference(refundRequest.getPaymentReference())
            .reason(refundRequest.getRefundReason())
            .refundStatus(SENTFORAPPROVAL)
            .reference(referenceUtil.getNext("RF"))
            .feeIds(refundRequest.getFeeIds())
            .createdBy(uid)
            .updatedBy(uid)
            .statusHistories(
                Arrays.asList(StatusHistory.statusHistoryWith()
                                  .createdBy(uid)
                                  .notes("Refund initiated")
                                  .status(SENTFORAPPROVAL.getName())
                                  .build()
                )
            )
            .build();
    }

    private String getRefundReason(String rawReason){
        if(rawReason.startsWith("RR")) {
            Optional<RefundReason> refundReasonOptional = refundReasonRepository.findByCode(rawReason);
            if(refundReasonOptional.isPresent()){
                return refundReasonOptional.get().getName();
            }
            throw new RefundReasonNotFoundException(rawReason);
        }
        return rawReason;
    }

}
