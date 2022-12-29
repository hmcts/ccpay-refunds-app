package uk.gov.hmcts.reform.refunds.services;

import com.microsoft.applicationinsights.boot.dependencies.apachecommons.lang3.EnumUtils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.eclipse.collections.impl.collector.Collectors2;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.dtos.requests.Notification;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundResubmitPayhubRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundSearchCriteria;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureReportDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.LargePayloadException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.PaymentFailureResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundFeeMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
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
import uk.gov.hmcts.reform.refunds.utils.DateUtil;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;
import uk.gov.hmcts.reform.refunds.validator.RefundValidator;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.UPDATEREQUIRED;

@Service
@SuppressWarnings({"PMD.PreserveStackTrace", "PMD.ExcessiveImports","PMD.TooManyMethods","PMD.GodClass"})
public class RefundsServiceImpl extends StateUtil implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR0[0-9][0-9]-[a-zA-Z]+)");

    private static final String OTHERREASONPATTERN = "Other - ";

    private static final String ROLEPATTERN = "^payments-refund(?:-approver?)?$";
    private static final String RETROSPECTIVE_REMISSION_REASON = "RR036";
    private static int reasonPrefixLength = 6;
    private static final String PAYMENT_REFUND = "payments-refund";

    private static int amountCompareValue = 1;

    private static final String CASH = "cash";

    private static final String POSTAL_ORDER = "postal order";

    private static final String BULK_SCAN = "bulk scan";

    private static final String REFUND_WHEN_CONTACTED = "RefundWhenContacted";

    private static final String SEND_REFUND = "SendRefund";

    private static final Predicate[] REF = new Predicate[0];

    DateUtil dateUtil = new DateUtil();

    private  long daysDifference;

    @Value("${refund.search.days}")
    private Integer numberOfDays;

    private final DateTimeFormatter formatter = dateUtil.getIsoDateTimeFormatter();

    @Autowired
    private RefundValidator refundValidator;


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

    @Autowired
    private RefundFeeMapper refundFeeMapper;

    private static final String REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER = "Refund initiated and sent to team leader";
    private static final Pattern EMAIL_ID_REGEX = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
        Pattern.CASE_INSENSITIVE
    );

    private static final String PAYMENTS_ROLE = "payments";

    @Override
    public RefundEvent[] retrieveActions(String reference) {
        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        return currentRefundState.nextValidEvents();
    }

    @Override
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException {
        validateRefundAmount(refundRequest);
        String reason =  validateRefundReason(refundRequest.getRefundReason());
        LOG.info("Refund reason before saving Refund >>>   {}", reason);
        refundRequest.setRefundReason(reason);
        String instructionType = null;

        if (refundRequest.getPaymentMethod() != null) {

            if (BULK_SCAN.equals(refundRequest.getPaymentChannel()) && (CASH.equals(refundRequest.getPaymentMethod())
                    || POSTAL_ORDER.equals(refundRequest.getPaymentMethod()))) {
                instructionType = REFUND_WHEN_CONTACTED;
            } else {
                instructionType = SEND_REFUND;
            }
        }
        IdamUserIdResponse uid = idamService.getUserId(headers);
        Refund refund = initiateRefundEntity(refundRequest, uid.getUid(), instructionType);
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

        return getRefundListDto(headers, refundList, idamUserIdResponse);
    }

    public RefundListDtoResponse getRefundListDto(MultiValueMap<String, String> headers,
                                                  Optional<List<Refund>> refundList, IdamUserIdResponse idamUserIdResponse) {

        List<String> refundRoles = idamUserIdResponse.getRoles().stream().filter(role -> role.matches(ROLEPATTERN))
            .collect(Collectors.toList());

        Optional<String> paymentRole = idamUserIdResponse.getRoles().stream().filter(role -> role.equals(PAYMENTS_ROLE)).findAny();

        if (refundList.isPresent() && !refundList.get().isEmpty()) {

            if (paymentRole.isPresent() && refundRoles.isEmpty()) {
                return RefundListDtoResponse
                    .buildRefundListWith()
                    .refundList(getRefundResponseDtoListForPaymentRole(headers, refundList.get()))
                    .build();
            } else {
                return RefundListDtoResponse
                    .buildRefundListWith()
                    .refundList(getRefundResponseDtoList(headers, refundList.get(), refundRoles))
                    .build();
            }
        } else {
            throw new RefundListEmptyException("Refund list is empty for given criteria");
        }
    }

    public List<RefundDto> getRefundResponseDtoList(MultiValueMap<String, String> headers, List<Refund> refundList, List<String> roles) {

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();
        List<RefundReason> refundReasonList = refundReasonRepository.findAll();

        if (!roles.isEmpty()) {
            Set<UserIdentityDataDto> userIdentityDataDtoSet = getUserIdentityDataDtoSet();

            // Filter Refunds List based on Refunds Roles and Update the user full name for created by
            List<String> userIdsWithGivenRoles = userIdentityDataDtoSet.stream().map(UserIdentityDataDto::getId).collect(
                Collectors.toList());

            refundList.forEach(refund -> {
                if (!userIdsWithGivenRoles.contains(refund.getCreatedBy())) {
                    UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(headers,refund.getCreatedBy());
                    contextStartListener.addUserToMap(PAYMENT_REFUND,userIdentityDataDto);
                    userIdentityDataDtoSet.add(userIdentityDataDto);
                    userIdsWithGivenRoles.add(userIdentityDataDto.getId());
                }
            });

            refundListDto =
                    populateRefundListDto(refundList, userIdsWithGivenRoles, refundReasonList, userIdentityDataDtoSet,
                            refundListDto);
        }
        return refundListDto;
    }

    @SuppressWarnings({"PMD.ConfusingTernary"})
    private Set<UserIdentityDataDto> getUserIdentityDataDtoSet() {
        Set<UserIdentityDataDto> userIdentityDataDtoSet;
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        if (null != contextStartListener.getUserMap() && null != contextStartListener.getUserMap().get(PAYMENT_REFUND)) {
            userIdentityDataDtoSet =  contextStartListener.getUserMap().get(PAYMENT_REFUND).stream().collect(
                    Collectors.toSet());
        } else {
            List<UserIdentityDataDto> userIdentityDataDtoList = idamService.getUsersForRoles(getAuthenticationHeaders(),
                    Arrays.asList(PAYMENT_REFUND,
                            "payments-refund-approver"));
            userMap.put(PAYMENT_REFUND,userIdentityDataDtoList);

            userIdentityDataDtoSet = userMap.get(PAYMENT_REFUND).stream().collect(Collectors.toSet());

        }
        return userIdentityDataDtoSet;
    }

    private List<RefundDto> populateRefundListDto(List<Refund> refundList, List<String> userIdsWithGivenRoles,
                                    List<RefundReason> refundReasonList,
                                    Set<UserIdentityDataDto> userIdentityDataDtoSet,
                                    List<RefundDto> refundListDto) {
        for (Refund refund : refundList.stream()
                .filter(e -> userIdsWithGivenRoles.stream()
                        .anyMatch(id -> id.equals(e.getCreatedBy())))
                .collect(Collectors.toList())) {
            String reason = getRefundReason(refund.getReason(), refundReasonList);
            Optional<UserIdentityDataDto> found = Optional.empty();
            for (UserIdentityDataDto dto : userIdentityDataDtoSet) {
                if (refund.getCreatedBy().equals(dto.getId())) {
                    found = Optional.of(dto);
                    break;
                }
            }
            found.ifPresent(userIdentityDataDto -> refundListDto.add(refundResponseMapper.getRefundListDto(
                    refund,
                    userIdentityDataDto,
                    reason
            )));
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
            String refundReason = RETROSPECTIVE_REMISSION_REASON.equals(refund.getReason()) ? RETROSPECTIVE_REMISSION_REASON :
                validateRefundReasonForNonRetroRemission(request.getRefundReason(),refund);
            LOG.info("Refund Reason in resubmitRefund {}",refundReason);
            refund.setAmount(request.getAmount());

            if (!(refund.getReason().equals(RETROSPECTIVE_REMISSION_REASON)) && !(RETROSPECTIVE_REMISSION_REASON.equals(refundReason))) {
                refund.setReason(refundReason);
            }

            BigDecimal totalRefundedAmount = getTotalRefundedAmountResubmitRefund(refund.getPaymentReference(), request.getAmount());

            // Remission update in payhub
            RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest
                .refundResubmitRequestPayhubWith()
                .refundReason(refundReason)
                .amount(request.getAmount())
                .feeId(refund.getFeeIds())
                .totalRefundedAmount(totalRefundedAmount)
                .build();

            LOG.info("TOTAL REFUNDED AMOUNT: {}", totalRefundedAmount);


            boolean payhubRemissionUpdateResponse = paymentService
                .updateRemissionAmountInPayhub(headers, refund.getPaymentReference(), refundResubmitPayhubRequest);

            LOG.info("PAYHUB REMISSION RESPONSE: {}", payhubRemissionUpdateResponse);


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
                refund.setRefundFees(request.getRefundFees().stream().map(refundFeeMapper::toRefundFee)
                    .collect(Collectors.toList()));
                if (null != request.getContactDetails()) {
                    validateContactDetails(request.getContactDetails());
                    refund.setContactDetails(request.getContactDetails());
                }

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
        final int reasonNameLength = 8;
        LOG.info("Refund reason {}", reason);
        if (reason == null || reason.isBlank()) {
            throw new InvalidRefundRequestException("Refund reason is required");
        }
        boolean matcher = REASONPATTERN.matcher(reason).find();
        LOG.info("Refund reason matcher {}", matcher);
        if (matcher) {
            String reasonCode = reason.split("-")[0];
            LOG.info("reasonCode in If loop {}",reasonCode);
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reasonCode);
            LOG.info("reasonName If loop {}",refundReason.getName());
            LOG.info("reasonCode If loop {}",refundReason.getCode());
            LOG.info("Final REASON >> {}",refundReason.getCode() + "-"
                + refundReason.getName().substring(reasonNameLength) + "-"
                + reason.substring(reasonPrefixLength));
            if (refundReason.getName().startsWith(OTHERREASONPATTERN)) {
                return refundReason.getCode() + "-"
                    + refundReason.getName().substring(reasonNameLength) + "-"
                    + reason.substring(reasonPrefixLength);
            } else {
                throw new InvalidRefundRequestException("Invalid reason selected");
            }

        } else {
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reason);
            LOG.info("reasonName Else loop {}",refundReason.getName());
            LOG.info("reasonCode Else loop {}",refundReason.getCode());
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

    private Refund initiateRefundEntity(RefundRequest refundRequest, String uid, String instructionType) throws CheckDigitException {
        return Refund.refundsWith()
            .amount(refundRequest.getRefundAmount())
            .ccdCaseNumber(refundRequest.getCcdCaseNumber())
            .paymentReference(refundRequest.getPaymentReference())
            .reason(refundRequest.getRefundReason())
            .refundStatus(SENTFORAPPROVAL)
            .reference(referenceUtil.getNext("RF"))
            .feeIds(refundRequest.getFeeIds())
            .serviceType(refundRequest.getServiceType())
            .createdBy(uid)
            .updatedBy(uid)
            .contactDetails(refundRequest.getContactDetails())
            .refundFees(refundRequest.getRefundFees().stream().map(refundFeeMapper::toRefundFee)
                            .collect(Collectors.toList()))
            .refundInstructionType(instructionType)
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
        LOG.info("rawReason in getRefundsReason >> {}", rawReason);
        if (null != rawReason && rawReason.startsWith("RR")) {
            List<RefundReason> refundReasonOptional = new ArrayList<>();
            for (RefundReason refundReason : refundReasonList) {
                LOG.info("refundReason code in getRefundsReason >> {}", refundReason.getCode());
                if (refundReason.getCode().equalsIgnoreCase(rawReason)) {
                    LOG.info("refundReason name in getRefundsReason >> {}", refundReason.getName());
                    refundReasonOptional.add(refundReason);
                    break;
                }
            }
            if (refundReasonOptional.isEmpty()) {
                return rawReason;
            } else {
                LOG.info("Refund Name {}", refundReasonOptional.get(0).getName());
                return refundReasonOptional.get(0).getName();
            }
        }
        LOG.info("Raw Reason being returned {}", rawReason);
        return rawReason;
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

    @Override
    public Optional<List<Refund>> getPaymentFailureReport(List<String> paymentReferenceList) {

        Optional<List<Refund>> refundList;

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

        refundList.forEach(refund -> paymentFailureDtoList.add(paymentFailureResponseMapper.getPaymentFailureDto(refund)));
        LOG.info("Converted payment failure report to response");

        return paymentFailureDtoList;
    }

    private void validateRefundAmount(RefundRequest refundRequest) {
        BigDecimal totalRefundedAmount;
        BigDecimal refundEligibleAmount;
        if (refundRequest.getRefundAmount().compareTo(refundRequest.getPaymentAmount()) > 0) {
            throw new InvalidRefundRequestException("The amount to refund can not be more than" + " " + "£" + refundRequest.getPaymentAmount());
        }

        BigDecimal refundAmount = getTotalRefundedAmountIssueRefund(refundRequest.getPaymentReference());

        refundEligibleAmount = refundRequest.getPaymentAmount().subtract(refundAmount);
        totalRefundedAmount = refundAmount.add(refundRequest.getRefundAmount());
        int amountCompare = totalRefundedAmount.compareTo(refundRequest.getPaymentAmount());

        if (amountCompare == amountCompareValue) {
            throw new InvalidRefundRequestException("The amount to refund can not be more than" + " " + "£" + refundEligibleAmount);
        }
    }

    private BigDecimal getTotalRefundedAmountResubmitRefund(String paymentReference, BigDecimal refundAmount) {
        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(paymentReference);
        BigDecimal totalRefundedAmount = BigDecimal.ZERO;

        if (refundsList.isPresent()) {
            List<Refund> refundsListStatus =
                    refundsList.get().stream().filter(refund -> refund.getRefundStatus().equals(
                            RefundStatus.ACCEPTED) || refund.getRefundStatus().equals(RefundStatus.APPROVED))
                            .collect(Collectors.toList());
            for (Refund ref : refundsListStatus) {
                totalRefundedAmount = ref.getAmount().add(totalRefundedAmount);
            }
            totalRefundedAmount = refundAmount.add(totalRefundedAmount);
        }
        return totalRefundedAmount;
    }

    private BigDecimal getTotalRefundedAmountIssueRefund(String paymentReference) {
        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(paymentReference);
        BigDecimal totalRefundedAmount = BigDecimal.ZERO;

        if (refundsList.isPresent()) {
            List<Refund> refundsListStatus =
                refundsList.get().stream().filter(refund -> !refund.getRefundStatus().equals(
                    RefundStatus.REJECTED))
                    .collect(Collectors.toList());
            for (Refund ref : refundsListStatus) {
                totalRefundedAmount = ref.getAmount().add(totalRefundedAmount);
            }
        }
        return totalRefundedAmount;
    }

    @SuppressWarnings({"PMD"})
    private void validateContactDetails(ContactDetails contactDetails) {
        Matcher matcher = null;
        if (null != contactDetails.getEmail()) {
            matcher = EMAIL_ID_REGEX.matcher(contactDetails.getEmail());
        }
        if (null == contactDetails.getNotificationType()
            || contactDetails.getNotificationType().isEmpty()) {
            throw new InvalidRefundRequestException("Notification should not be null or empty");
        } else if (!EnumUtils
            .isValidEnum(Notification.class, contactDetails.getNotificationType())) {
            throw new InvalidRefundRequestException("Contact details should be email or letter");
        } else if (Notification.EMAIL.getNotification()
            .equals(contactDetails.getNotificationType())
            && (null == contactDetails.getEmail()
            || contactDetails.getEmail().isEmpty())) {
            throw new InvalidRefundRequestException("Email id should not be empty");
        } else if (Notification.LETTER.getNotification()
            .equals(contactDetails.getNotificationType())
            && (null == contactDetails.getPostalCode()
            || contactDetails.getPostalCode().isEmpty())) {
            throw new InvalidRefundRequestException("Postal code should not be empty");
        } else if (Notification.EMAIL.getNotification()
            .equals(contactDetails.getNotificationType())
            && null != matcher && !matcher.find()) {
            throw new InvalidRefundRequestException("Email id is not valid");
        }
    }

    private  String  validateRefundReasonForNonRetroRemission(String reason, Refund refund) {

        return validateRefundReason(reason == null ? refund.getReason() : reason);
    }

    @Override
    @SuppressWarnings({"PMD.ConfusingTernary"})
    public List<RefundLiberata> search(Optional<String> startDateTimeString, Optional<String> endDateTimeString, String refundReference) {

        List<String> referenceList =  new ArrayList<>();
        List<RefundLiberata> refundLiberatas = new ArrayList<>();
        List<Refund> refundListWithAccepted;
        List<Refund> refundListNotInDateRange;

        refundValidator.validate(startDateTimeString, endDateTimeString);

        Date fromDateTime = getFromDateTime(startDateTimeString);

        Date  toDateTime = getToDateTime(endDateTimeString, fromDateTime);

        validateV2ApiDateRange(fromDateTime,toDateTime);

        List<Refund> refundList = refundsRepository.findAll(searchByCriteria(getSearchCriteria(fromDateTime, toDateTime, refundReference)));
        if (!refundList.isEmpty()) {
            refundListWithAccepted = refundList.stream().filter(refund -> refund.getRefundStatus().equals(
                    RefundStatus.APPROVED))
                .collect(Collectors.toList());
            for (Refund ref : refundListWithAccepted) {
                referenceList.add(ref.getPaymentReference());
            }
        } else {
            throw new RefundNotFoundException("No refunds available for the given date range");
        }

        if (startDateTimeString.isPresent() && endDateTimeString.isPresent()) {
            refundListNotInDateRange = refundsRepository.findByDatesBetween(fromDateTime,toDateTime);
        } else {

            refundListNotInDateRange = refundsRepository.findAllByPaymentReference(refundListWithAccepted.get(0).getPaymentReference(),
                                                                                   refundListWithAccepted.get(0).getReference());
        }

        List<PaymentDto> paymentData =  paymentService.fetchPaymentResponse(referenceList);

        Map<String, BigDecimal> groupByPaymentReference =
            refundListWithAccepted.stream().collect(Collectors.groupingBy(Refund::getPaymentReference,
                                                                          Collectors2.summingBigDecimal(Refund::getAmount)));

        Map<String, BigDecimal> groupByPaymentReferenceForNotInDateRange =
            refundListNotInDateRange.stream().collect(Collectors.groupingBy(Refund::getPaymentReference,
                                                                            Collectors2.summingBigDecimal(Refund::getAmount)));

        Map<String, BigDecimal> avlBalance;

        avlBalance = calculateAvailableBalance(groupByPaymentReference,groupByPaymentReferenceForNotInDateRange);

        Map<String, BigDecimal> finalAvlBalance = avlBalance;
        refundListWithAccepted.stream()
            .filter(e -> paymentData.stream()
                .anyMatch(id -> id.getPaymentReference().equals(e.getPaymentReference())))
            .collect(Collectors.toList())
            .forEach(refund -> {
                LOG.info("refund: {}", refund);
                refundLiberatas.add(refundResponseMapper.getRefundLibrata(
                    refund,
                    paymentData.stream()
                        .filter(dto -> refund.getPaymentReference().equals(dto.getPaymentReference()))
                        .findAny().get(),
                    finalAvlBalance
                ));
            });
        return refundLiberatas;
    }

    @SuppressWarnings({"PMD.UselessParentheses"})
    public  Specification<Refund> searchByCriteria(RefundSearchCriteria searchCriteria) {
        return ((root, query, cb) -> getPredicate(root, cb, searchCriteria, query));
    }

    public Predicate getPredicate(
        Root<Refund> root,
        CriteriaBuilder cb,
        RefundSearchCriteria searchCriteria, CriteriaQuery<?> query) {
        List<Predicate> predicates = new ArrayList<>();

        final Expression<Date> dateUpdatedExpr = cb.function(
            "date_trunc",
            Date.class,
            cb.literal("seconds"),
            root.get("dateUpdated")
        );

        if (searchCriteria.getStartDate() != null && searchCriteria.getEndDate() != null) {
            predicates.add(cb.between(
                dateUpdatedExpr,
                searchCriteria.getStartDate(),
                searchCriteria.getEndDate()
            ));
        }
        if (null != searchCriteria.getRefundReference()) {
            predicates.add(cb.equal(root.get("reference"), searchCriteria.getRefundReference()));
        }
        query.groupBy(root.get("id"));
        return cb.or(predicates.toArray(REF));
    }

    private Date getFromDateTime(@PathVariable(name = "start_date") Optional<String> startDateTimeString) {
        return Optional.ofNullable(startDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(org.joda.time.LocalDateTime::toDate)
            .orElse(null);
    }

    private Date getToDateTime(@PathVariable(name = "end_date") Optional<String> endDateTimeString, Date fromDateTime) {
        return Optional.ofNullable(endDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(s -> fromDateTime != null && s.getHourOfDay() == 0 ? s.plusDays(1).minusSeconds(1).toDate() : s.toDate())
            .orElse(null);
    }

    private RefundSearchCriteria getSearchCriteria(Date fromDateTime, Date toDateTime, String refundReference) {
        return RefundSearchCriteria
            .searchCriteriaWith()
            .startDate(fromDateTime)
            .endDate(toDateTime)
            .refundReference(refundReference)
            .build();

    }

    @SuppressWarnings({"PMD.ConfusingTernary"})
    private  Map<String, BigDecimal> calculateAvailableBalance(Map<String, BigDecimal> groupByPaymentReference,
                                                               Map<String, BigDecimal> groupByPaymentReferenceForNotInDateRange) {

        BigDecimal amountSecond;
        BigDecimal sumAmount;
        Map<String, BigDecimal> avlBalance = new ConcurrentHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : groupByPaymentReference.entrySet()) {
            String key = entry.getKey();
            BigDecimal amountFirst = entry.getValue();
            amountSecond = groupByPaymentReferenceForNotInDateRange.get(key);
            if (null != amountSecond) {
                sumAmount = amountFirst.add(amountSecond);
            } else {
                sumAmount = amountFirst;
            }
            avlBalance.put(key,sumAmount);
        }
        return  avlBalance;
    }

    @SuppressWarnings({"PMD.LawOfDemeter"})
    private void validateV2ApiDateRange(Date fromDateTime, Date toDateTime) {

        if (null != fromDateTime && null != toDateTime) {
            daysDifference = ChronoUnit.DAYS.between(fromDateTime.toInstant(), toDateTime.toInstant());
        }

        if (daysDifference > numberOfDays) {

            throw new LargePayloadException("Date range exceeds the maximum supported by the system");
        }
    }

    private List<RefundDto> getRefundResponseDtoListForPaymentRole(MultiValueMap<String, String> headers, List<Refund> refundList) {

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();
        List<RefundReason> refundReasonList = refundReasonRepository.findAll();
        for (Refund refund: refundList) {
            UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(headers,
                                                                                      refund.getCreatedBy());

            String reason = getRefundReason(refund.getReason(), refundReasonList);
            if (refund.getCreatedBy().equals(userIdentityDataDto.getId())) {
                refundListDto.add(refundResponseMapper.getRefundListDto(
                    refund,
                    userIdentityDataDto,
                    reason
                ));
            }
        }
        return refundListDto;
    }

}
