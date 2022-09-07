package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundResubmitPayhubRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureReportDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundReasonNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.PaymentFailureResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.UPDATEREQUIRED;

@Service
@SuppressWarnings({"PMD.PreserveStackTrace", "PMD.ExcessiveImports", "PMD.GodClass"})
public class RefundsServiceImpl extends StateUtil implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR0[0-9][0-9]-[a-zA-Z]+)");

    private static final String OTHERREASONPATTERN = "Other - ";

    private static final String ROLEPATTERN = "^.*refund.*$";
    private static final String RETROSPECTIVE_REMISSION_REASON = "RR036";
    private static int reasonPrefixLength = 6;
    private static final String PAYMENT_REFUND = "payments-refund";

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
    private PaymentFailureResponseMapper paymentFailureResponseMapper;

    @Autowired
    private StatusHistoryResponseMapper statusHistoryResponseMapper;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ContextStartListener contextStartListener;

    private static final String REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER = "Refund initiated and sent to team leader";

    @Override
    public RefundEvent[] retrieveActions(String reference) {
        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        return currentRefundState.nextValidEvents();
    }

    @Override
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException {
        validateRefundRequest(refundRequest);
        IdamUserIdResponse uid = idamService.getUserId(headers);
        Refund refund = initiateRefundEntity(refundRequest, uid.getUid());
        refundsRepository.save(refund);
        LOG.info("Refund saved");
        return RefundResponse.buildRefundResponseWith()
            .refundReference(refund.getReference())
            .build();
    }

    @Override
    public RefundListDtoResponse getRefundList(String status, MultiValueMap<String, String> headers,
                                               String ccdCaseNumber, String excludeCurrentUser) {

        Optional<List<Refund>> refundList = Optional.empty();

        //Get the userId
        IdamUserIdResponse idamUserIdResponse = idamService.getUserId(headers);
        LOG.info("idamUserIdResponse: {}", idamUserIdResponse);
        //Return Refund list based on ccdCaseNumber if its not blank
        if (StringUtils.isNotBlank(ccdCaseNumber)) {
            refundList = refundsRepository.findByCcdCaseNumber(ccdCaseNumber);
        } else if (StringUtils.isNotBlank(status)) {
            RefundStatus refundStatus = RefundStatus.getRefundStatus(status);
            //get the refund list except the self uid
            refundList = SENTFORAPPROVAL.getName().equalsIgnoreCase(status) && "true".equalsIgnoreCase(
                excludeCurrentUser) ? refundsRepository.findByRefundStatusAndUpdatedByIsNot(
                refundStatus,
                idamUserIdResponse.getUid()
            ) : refundsRepository.findByRefundStatus(refundStatus);
        }

        LOG.info("refundList: {}", refundList);
        // Get Refunds related Roles from logged in user
        List<String> roles = idamUserIdResponse.getRoles().stream().filter(role -> role.matches(ROLEPATTERN))
            .collect(Collectors.toList());
        LOG.info("roles: {}", roles);
        return getRefundListDto(headers, refundList, roles);
    }

    public RefundListDtoResponse getRefundListDto(MultiValueMap<String, String> headers, Optional<List<Refund>> refundList, List<String> roles) {

        if (refundList.isPresent() && !refundList.get().isEmpty()) {
            return RefundListDtoResponse
                .buildRefundListWith()
                .refundList(getRefundResponseDtoList(headers, refundList.get(), roles))
                .build();
        } else {
            throw new RefundListEmptyException("Refund list is empty for given criteria");
        }
    }

    @SuppressWarnings({"PMD.ConfusingTernary"})
    public List<RefundDto> getRefundResponseDtoList(MultiValueMap<String, String> headers, List<Refund> refundList, List<String> roles) {

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();
        List<RefundReason> refundReasonList = refundReasonRepository.findAll();
        Set<UserIdentityDataDto> userIdentityDataDtoSet;
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        if (!roles.isEmpty()) {
            LOG.info("Fetching cached refunds user list from IDAM...");
            if (!contextStartListener.getUserMap().isEmpty()) {
                userIdentityDataDtoSet =  contextStartListener.getUserMap().get(PAYMENT_REFUND).stream().collect(
                    Collectors.toSet());
            } else {
                List<UserIdentityDataDto> userIdentityDataDtoList = idamService.getUsersForRoles(getAuthenticationHeaders(),
                                                                                                 Arrays.asList(PAYMENT_REFUND,
                                                                                                               "payments-refund-approver"));
                userMap.put(PAYMENT_REFUND,userIdentityDataDtoList);

                userIdentityDataDtoSet = userMap.get(PAYMENT_REFUND).stream().collect(Collectors.toSet());

            }
            // Filter Refunds List based on Refunds Roles and Update the user full name for created by
            List<String> userIdsWithGivenRoles = userIdentityDataDtoSet.stream().map(UserIdentityDataDto::getId).collect(
                Collectors.toList());
            for (Refund refund1 : refundList) {
                if (!userIdsWithGivenRoles.contains(refund1.getCreatedBy())) {
                    UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(headers,
                                                                                              refund1.getCreatedBy());
                    contextStartListener.addUserToMap(PAYMENT_REFUND, userIdentityDataDto);
                    userIdentityDataDtoSet.add(userIdentityDataDto);
                    userIdsWithGivenRoles.add(userIdentityDataDto.getId());
                }
            }
            refundList.forEach(refund -> {
                if (!userIdsWithGivenRoles.contains(refund.getCreatedBy())) {
                    UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(headers,refund.getCreatedBy());
                    contextStartListener.addUserToMap(PAYMENT_REFUND,userIdentityDataDto);
                    userIdentityDataDtoSet.add(userIdentityDataDto);
                    userIdsWithGivenRoles.add(userIdentityDataDto.getId());
                }
            });
            for (Refund refund : refundList.stream()
                .filter(e -> userIdsWithGivenRoles.stream()
                    .anyMatch(id -> id.equals(e.getCreatedBy())))
                .collect(Collectors.toList())) {
                String reason = getRefundReason(refund.getReason(), refundReasonList);
                LOG.info("refund: {}", refund);
                Optional<UserIdentityDataDto> found = Optional.empty();
                for (UserIdentityDataDto dto : userIdentityDataDtoSet) {
                    if (refund.getCreatedBy().equals(dto.getId())) {
                        found = Optional.of(dto);
                        break;
                    }
                }
                if (found.isPresent()) {
                    refundListDto.add(refundResponseMapper.getRefundListDto(
                        refund,
                        found.get(),
                        reason
                    ));
                }
            }
        }
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

    @Override
    public List<RejectionReasonResponse> getRejectedReasons() {
        // Getting names from Rejection Reasons List object
        List<RejectionReasonResponse> list = new ArrayList<>();
        for (RejectionReason reason : rejectionReasonRepository.findAll()) {
            RejectionReasonResponse build = RejectionReasonResponse.rejectionReasonWith()
                .code(reason.getCode())
                .name(reason.getName())
                .build();
            list.add(build);
        }
        return list;
    }

    @Override
    public StatusHistoryResponseDto getStatusHistory(MultiValueMap<String, String> headers, String reference) {
        List<StatusHistory> statusHistories = null;
        Boolean isLastUpdatedByCurrentUser = false;
        if (null != reference) {

            Refund refund = refundsRepository.findByReferenceOrThrow(reference);

            statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund);

            IdamUserIdResponse idamUserIdResponse = idamService.getUserId(headers);

            isLastUpdatedByCurrentUser = isLastUpdatedByCurrentUser(idamUserIdResponse.getUid(), refund);

        }

        return getStatusHistoryDto(headers, statusHistories, isLastUpdatedByCurrentUser);
    }

    @Override
    public ResubmitRefundResponseDto resubmitRefund(String reference, ResubmitRefundRequest request,
                                                    MultiValueMap<String, String> headers) {

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);

        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());


        if (currentRefundState.getRefundStatus().equals(UPDATEREQUIRED)) {

            // Refund Reason Validation
            String refundReason = RETROSPECTIVE_REMISSION_REASON.equals(refund.getReason()) ? RETROSPECTIVE_REMISSION_REASON : validateRefundReason(
                request.getRefundReason());

            BigDecimal refundAmount = request.getAmount() == null ? refund.getAmount() : request.getAmount();

            refund.setReason(refundReason);
            refund.setAmount(refundAmount);
            // Remission update in payhub
            RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest
                .refundResubmitRequestPayhubWith()
                .refundReason(refundReason)
                .amount(refundAmount)
                .feeId(refund.getFeeIds())
                .build();

            boolean payhubRemissionUpdateResponse = paymentService
                .updateRemissionAmountInPayhub(headers, refund.getPaymentReference(), refundResubmitPayhubRequest);

            if (payhubRemissionUpdateResponse) {
                // Update Status History table
                IdamUserIdResponse idamUserIdResponse = idamService.getUserId(headers);
                refund.setUpdatedBy(idamUserIdResponse.getUid());
                List<StatusHistory> statusHistories = new ArrayList<>(refund.getStatusHistories());
                refund.setUpdatedBy(idamUserIdResponse.getUid());
                statusHistories.add(StatusHistory.statusHistoryWith()
                                        .createdBy(idamUserIdResponse.getUid())
                                        .status(SENTFORAPPROVAL.getName())
                                        .notes(REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER)
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
        }
        throw new ActionNotFoundException("Action not allowed to proceed");
    }

    @Override
    @Transactional
    public void deleteRefund(String reference) {
        long records = refundsRepository.deleteByReference(reference);
        if (records == 0) {
            throw new RefundNotFoundException("No records found for given refund reference");
        }
    }

    @Override
    public List<Refund> getRefundsForPaymentReference(String paymentReference) {
        Optional<List<Refund>> refundList = refundsRepository.findByPaymentReference(paymentReference);
        if (refundList.isPresent() && !refundList.get().isEmpty()) {
            return refundList.get();
        }
        throw new RefundNotFoundException("Refunds not found for payment reference " + paymentReference);
    }

    private String validateRefundReason(String reason) {

        if (reason == null || reason.isBlank()) {
            throw new InvalidRefundRequestException("Refund reason is required");
        }
        Boolean matcher = REASONPATTERN.matcher(reason).find();
        if (matcher) {
            String reasonCode = reason.split("-")[0];
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reasonCode);
            if (refundReason.getName().startsWith(OTHERREASONPATTERN)) {
                return refundReason.getName().split(OTHERREASONPATTERN)[1] + "-" + reason.substring(reasonPrefixLength);
            } else {
                throw new InvalidRefundRequestException("Invalid reason selected");
            }

        } else {
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reason);
            if (refundReason.getName().startsWith(OTHERREASONPATTERN)) {
                throw new InvalidRefundRequestException("reason required");
            }
            return refundReason.getCode();
        }
    }

    private Boolean isLastUpdatedByCurrentUser(String uid, Refund refund) {
        return refund.getUpdatedBy().equals(uid);
    }

    private StatusHistoryResponseDto getStatusHistoryDto(MultiValueMap<String, String> headers,
                                                         List<StatusHistory> statusHistories, Boolean isUpdatedByCurrentUser) {

        List<StatusHistoryDto> statusHistoryDtos = new ArrayList<>();

        if (null != statusHistories && !statusHistories.isEmpty()) {
            //Distinct createdBy UID
            Set<String> distintUidSet = new HashSet<>();
            for (StatusHistory history : statusHistories) {
                String createdBy = history.getCreatedBy();
                distintUidSet.add(createdBy);
            }

            //Map UID -> User full name
            Map<String, UserIdentityDataDto> userFullNameMap = getIdamUserDetails(headers, distintUidSet);

            for (StatusHistory statusHistory : statusHistories) {
                statusHistoryDtos.add(statusHistoryResponseMapper.getStatusHistoryDto(
                    statusHistory,
                    userFullNameMap.get(statusHistory.getCreatedBy())
                ));
            }
        }
        return StatusHistoryResponseDto.statusHistoryResponseDtoWith()
            .lastUpdatedByCurrentUser(isUpdatedByCurrentUser)
            .statusHistoryDtoList(statusHistoryDtos)
            .build();
    }

    private Map<String, UserIdentityDataDto> getIdamUserDetails(MultiValueMap<String, String> headers, Set<String> distintUidSet) {
        Map<String, UserIdentityDataDto> userFullNameMap = new ConcurrentHashMap<>();
        for (String userId : distintUidSet) {
            userFullNameMap.put(
                userId,
                idamService.getUserIdentityData(headers, userId)
            );
        }
        return userFullNameMap;
    }

    private void validateRefundRequest(RefundRequest refundRequest) {

        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(refundRequest.getPaymentReference());

        if (refundsList.isPresent()) {
            List<String> nonRejectedFeeList = new ArrayList<>();
            for (Refund refund : refundsList.get()) {
                if (!refund.getRefundStatus().equals(
                    RefundStatus.REJECTED)) {
                    String feeIds = refund.getFeeIds();
                    nonRejectedFeeList.add(feeIds);
                }
            }

            List<String> feeIdsofRequestedRefund;
            if (refundRequest.getFeeIds().contains(",")) {
                List<String> list = new ArrayList<>();
                for (String s : refundRequest.getFeeIds().split(
                    ",")) {
                    list.add(s);
                }
                feeIdsofRequestedRefund = list;
            } else {
                feeIdsofRequestedRefund = Arrays.asList(refundRequest.getFeeIds());
            }

            for (String feeId : feeIdsofRequestedRefund) {
                for (String nonRejectFee : nonRejectedFeeList) {
                    if (nonRejectFee.contains(feeId)) {
                        throw new InvalidRefundRequestException("Refund is already requested for this payment");
                    }
                }

            }
        }

        refundRequest.setRefundReason(validateRefundReason(refundRequest.getRefundReason()));

    }

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
                                  .notes(REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER)
                                  .status(SENTFORAPPROVAL.getName())
                                  .build()
                )
            )
            .build();
    }

    private String getRefundReason(String rawReason, List<RefundReason> refundReasonList) {
        if (null != rawReason && rawReason.startsWith("RR")) {
            List<RefundReason> refundReasonOptional = new ArrayList<>();
            for (RefundReason refundReason : refundReasonList) {
                if (refundReason.getCode().equalsIgnoreCase(rawReason)) {
                    refundReasonOptional.add(refundReason);
                }
            }
            if (!refundReasonOptional.isEmpty()) {
                return refundReasonOptional.get(0).getName();
            }
            throw new RefundReasonNotFoundException(rawReason);
        }
        return rawReason;
    }

    @Override
    public Optional<List<Refund>> getPaymentFailureReport(List<String> paymentReferenceList) {

        Optional<List<Refund>> refundList = Optional.empty();

        List<RefundStatus> refundStatusFilterNotIn = Arrays.asList(RefundStatus.ACCEPTED, RefundStatus.REJECTED);
        LOG.info("Payment failure report requested for: {}", paymentReferenceList.size());
        refundList = refundsRepository.findByPaymentReferenceInAndRefundStatusNotIn(
            paymentReferenceList,
            refundStatusFilterNotIn
        );
        LOG.info("Payment failure report retrieved");
        return refundList;
    }

    @Override
    public PaymentFailureReportDtoResponse getPaymentFailureDtoResponse(List<Refund> refundList) {
        return PaymentFailureReportDtoResponse.buildPaymentFailureListWith().paymentFailureDto(
            getPaymentFailureDtoList(refundList)).build();
    }

    public List<PaymentFailureDto> getPaymentFailureDtoList(List<Refund> refundList) {

        List<PaymentFailureDto> paymentFailureDtoList = new ArrayList<>();

        refundList.forEach(refund -> {
            paymentFailureDtoList.add(paymentFailureResponseMapper.getPaymentFailureDto(refund));
        });
        LOG.info("Converted payment failure report to response");

        return paymentFailureDtoList;
    }

    private MultiValueMap<String, String>  getAuthenticationHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.add("Authorization", getAccessToken());
        return inputHeaders;
    }

    private String getAccessToken() {
        IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
        return idamTokenResponse.getAccessToken();
    }
}
