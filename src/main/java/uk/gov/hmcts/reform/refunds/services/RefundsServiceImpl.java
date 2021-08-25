package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.*;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;

@Service
@SuppressWarnings("PMD.PreserveStackTrace")
public class RefundsServiceImpl extends StateUtil implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR004-[a-zA-Z]+)|(RR004$)");

    private static final Pattern STATUSPATTERN = Pattern.compile("[^rejected]");

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
    public RefundListDtoResponse getRefundList(String status, MultiValueMap<String, String> headers, String ccdCaseNumber, String excludeCurrentUser) {
        //Get the userId
        String uid = idamService.getUserId(headers);
        Optional<List<Refund>> refundList;

        //Return Refund list based on ccdCaseNumber if its not blank
        if (ccdCaseNumber != null && !ccdCaseNumber.isBlank()) {
            refundList = refundsRepository.findByCcdCaseNumber(ccdCaseNumber);
            return getRefundListDto(headers, refundList);
        }

        RefundStatus refundStatus = RefundStatus.getRefundStatus(status.toLowerCase());

        //get the refund list except the self uid
        refundList = SENTFORAPPROVAL.getName().equalsIgnoreCase(status) && "true".equalsIgnoreCase(excludeCurrentUser) ? refundsRepository.findByRefundStatusAndCreatedByIsNot(
            refundStatus,
            uid
        ) : refundsRepository.findByRefundStatus(refundStatus);

        return getRefundListDto(headers, refundList);
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
        Map<String, UserIdentityDataDto> userFullNameMap = new ConcurrentHashMap<>();
        distintUIDSet.forEach(userId -> userFullNameMap.put(
            userId,
            idamService.getUserIdentityData(headers, userId)
        ));

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();

        //Update the user full name for created by
        refundList
            .forEach(refund ->
                         refundListDto.add(refundResponseMapper.getRefundListDto(
                             refund,
                             userFullNameMap.get(refund.getCreatedBy())
                         )));

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
    public List<StatusHistoryDto> getStatusHistory(String reference) {

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);

        List<StatusHistory> statusHistories = statusHistoryRepository.findByRefund(refund);

        return getStatusHistoryDto(statusHistories);
    }

    private List<StatusHistoryDto> getStatusHistoryDto(List<StatusHistory> statusHistories) {

        List<StatusHistoryDto> statusHistoryDtos = new ArrayList<>();

        if (null != statusHistories && !statusHistories.isEmpty()) {
            statusHistories
                    .forEach(statusHistory ->
                            statusHistoryDtos.add(statusHistoryResponseMapper.getStatusHistoryDto(
                                    statusHistory
                            )));
        }

        return statusHistoryDtos;
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

        Boolean matcher = REASONPATTERN.matcher(refundRequest.getRefundReason()).find();
        if (matcher) {
            if (refundRequest.getRefundReason().length() > reasonPrefixLength) {
                refundRequest.setRefundReason(refundRequest.getRefundReason().substring(reasonPrefixLength));
            } else {
                throw new InvalidRefundRequestException("Invalid reason selected");
            }
        } else {
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(refundRequest.getRefundReason());
            refundRequest.setRefundReason(refundReason.getCode());
        }

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
}
