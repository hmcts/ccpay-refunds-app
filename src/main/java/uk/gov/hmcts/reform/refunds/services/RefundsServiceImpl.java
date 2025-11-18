package uk.gov.hmcts.reform.refunds.services;

import com.microsoft.applicationinsights.boot.dependencies.apachecommons.lang3.EnumUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
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
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.ReissueExpiredRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.PaymentFailureResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundFeeMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundFees;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundFeesRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.DateUtil;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.RefundServiceRoleUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;
import uk.gov.hmcts.reform.refunds.validator.RefundValidator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.APPROVED;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.REISSUED;
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

    private static final String REFUND_CLOSED_BY_CASE_WORKER = "Refund closed by case worker";
    private static final String REFUND_REISSUED_BY = "Refund reissued by";
    private static final String REFUND_APPROVED_BY_SYSTEM = "Refund approved by system";

    private static final Predicate[] REF = new Predicate[0];

    DateUtil dateUtil = new DateUtil();

    private long daysDifference;

    private static final int REASON_CODE_END = 6;

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

    @Autowired
    private RefundServiceRoleUtil refundServiceRoleUtil;

    @Autowired
    private RefundFeesRepository refundFeesRepository;


    private static final String REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER = "Refund initiated and sent to team leader";


    private static final String IDAM_USER_NOT_FOUND_MSG = "User not found";

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
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers, IdamUserIdResponse idamUserIdResponse)
        throws CheckDigitException {

        validateRefundAmount(refundRequest);
        String reason = validateRefundReason(refundRequest.getRefundReason());
        refundRequest.setRefundReason(reason);

        String instructionType = null;
        if (refundRequest.getPaymentMethod() != null) {
            instructionType = SEND_REFUND;
        }

        Refund refund = initiateRefundEntity(refundRequest, idamUserIdResponse.getUid(), instructionType);
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
        LOG.info("idamUserIdResponse uid: {}", idamUserIdResponse.getUid());
        LOG.info("idamUserIdResponse roles: {}", idamUserIdResponse.getRoles());
        List<String> serviceList = refundServiceRoleUtil.getServiceNameFromUserRoles(idamUserIdResponse.getRoles());
        LOG.info("serviceList {}", serviceList.toString());

        //Return Refund list based on ccdCaseNumber if it is not blank
        if (StringUtils.isNotBlank(ccdCaseNumber)) {
            refundList = serviceList.isEmpty() ? refundsRepository.findByCcdCaseNumber(ccdCaseNumber)
                : refundsRepository.findByCcdCaseNumberAndServiceTypeInIgnoreCase(ccdCaseNumber, serviceList);
        } else if (StringUtils.isNotBlank(status)) {
            RefundStatus refundStatus = RefundStatus.getRefundStatus(status);

            //get the refund list except the self uid
            refundList = SENTFORAPPROVAL.getName().equalsIgnoreCase(status) && "true".equalsIgnoreCase(
                excludeCurrentUser) ? refundsRepository.findByRefundStatusAndUpdatedByIsNotAndServiceTypeInIgnoreCase(
                refundStatus,
                idamUserIdResponse.getUid(),
                serviceList
            ) : refundsRepository.findByRefundStatusAndServiceTypeInIgnoreCase(refundStatus, serviceList);
        }

        return getRefundListDto(headers, refundList, idamUserIdResponse);
    }


    public RefundListDtoResponse getRefundListDto(MultiValueMap<String, String> headers,
                                                  Optional<List<Refund>> refundList, IdamUserIdResponse idamUserIdResponse) {

        List<String> refundRoles = idamUserIdResponse.getRoles().stream().filter(role -> role.matches(ROLEPATTERN))
            .collect(Collectors.toList());

        Optional<String> paymentRole = idamUserIdResponse.getRoles().stream().filter(role -> role.equals(PAYMENTS_ROLE)).findAny();

        if (refundList.isPresent() && !refundList.get().isEmpty()) {
            LOG.info("Refund List is finite");
            if (paymentRole.isPresent() && refundRoles.isEmpty()) {
                LOG.info("Payment role is present but refund roles are absent");
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
            LOG.info("Refund list is empty for given criteria");
            // Return empty list instead of throwing exception
            return RefundListDtoResponse.buildRefundListWith().refundList(Collections.emptyList()).build();
        }
    }


    public List<RefundDto> getRefundResponseDtoList(MultiValueMap<String, String> headers, List<Refund> refundList, List<String> roles) {

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();
        List<RefundReason> refundReasonList = refundReasonRepository.findAll();

        if (!roles.isEmpty()) {
            Set<UserIdentityDataDto> userIdentityDataDtoSet = getUserIdentityDataDtoSet();
            LOG.info("Roles are not empty in getRefundResponseDtoList");
            // Filter Refunds List based on Refunds Roles and Update the user full name for created by
            List<String> userIdsWithGivenRoles = userIdentityDataDtoSet.stream().map(UserIdentityDataDto::getId).collect(
                Collectors.toList());

            refundList.forEach(refund -> {
                if (!userIdsWithGivenRoles.contains(refund.getCreatedBy())) {
                    UserIdentityDataDto userIdentityDataDto;
                    try {
                        userIdentityDataDto =
                            idamService.getUserIdentityData(headers, refund.getCreatedBy());

                    } catch (UserNotFoundException userNotFoundException) {
                        LOG.warn("Refund {} created by UID {} not available for case {}",
                                 refund.getId(), refund.getCreatedBy(), refund.getCcdCaseNumber());
                        userIdentityDataDto = new UserIdentityDataDto(
                            IDAM_USER_NOT_FOUND_MSG,
                            IDAM_USER_NOT_FOUND_MSG,
                            refund.getCreatedBy(),
                            Collections.<String>emptyList()
                        );
                    }
                    contextStartListener.addUserToMap(PAYMENT_REFUND, userIdentityDataDto);
                    userIdentityDataDtoSet.add(userIdentityDataDto);
                    userIdsWithGivenRoles.add(userIdentityDataDto.getId());
                }
            });
            if (null != userIdsWithGivenRoles) {
                LOG.info("userIdsWithGivenRoles size {}", userIdsWithGivenRoles.size());
            }
            refundListDto =
                populateRefundListDto(refundList, userIdsWithGivenRoles, refundReasonList, userIdentityDataDtoSet,
                                      refundListDto
                );
        }
        return refundListDto;
    }

    @SuppressWarnings({"PMD.ConfusingTernary"})
    private Set<UserIdentityDataDto> getUserIdentityDataDtoSet() {
        Set<UserIdentityDataDto> userIdentityDataDtoSet;
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        if (null != contextStartListener.getUserMap() && null != contextStartListener.getUserMap().get(PAYMENT_REFUND)) {
            userIdentityDataDtoSet = contextStartListener.getUserMap().get(PAYMENT_REFUND).stream().collect(
                Collectors.toSet());
        } else {
            List<UserIdentityDataDto> userIdentityDataDtoList = idamService.getUsersForRoles(
                getAuthenticationHeaders(),
                Arrays.asList(
                    PAYMENT_REFUND,
                    "payments-refund-approver"
                )
            );
            userMap.put(PAYMENT_REFUND, userIdentityDataDtoList);

            userIdentityDataDtoSet = userMap.get(PAYMENT_REFUND).stream().collect(Collectors.toSet());

        }
        LOG.info("userIdentityDataDtoSet size in getUserIdentityDataDtoSet {}",userIdentityDataDtoSet.stream().count());
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
        LOG.info("refundListDto size in populateRefundListDto {}",refundList.size());
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

        IdamUserIdResponse idamUserIdResponse = idamService.getUserId(headers);
        refundServiceRoleUtil.validateRefundRoleWithServiceName(idamUserIdResponse.getRoles(), refund.getServiceType());

        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());

        if (currentRefundState.getRefundStatus().equals(UPDATEREQUIRED)) {

            // Refund Reason Validation
            String refundReason = RETROSPECTIVE_REMISSION_REASON.equals(refund.getReason()) ? RETROSPECTIVE_REMISSION_REASON :
                validateRefundReasonForNonRetroRemission(request.getRefundReason(), refund);
            LOG.info("Refund Reason in resubmitRefund {}", refundReason);
            refund.setAmount(request.getAmount());

            if (!(refund.getReason().equals(RETROSPECTIVE_REMISSION_REASON)) && !(RETROSPECTIVE_REMISSION_REASON.equals(
                refundReason))) {
                refund.setReason(refundReason);
            }

            BigDecimal refundAmount = getTotalRefundedAmountIssueRefund(refund.getPaymentReference());
            refundAmount = refundAmount.subtract(refund.getAmount());
            BigDecimal totalRefundedAmount = refundAmount.add(request.getAmount());
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
                refund.setRefundFees(toRefundFeeMap(refund,request));
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
        if (reason == null || reason.isBlank()) {
            throw new InvalidRefundRequestException("Refund reason is required");
        }
        boolean matcher = REASONPATTERN.matcher(reason).find();
        if (matcher) {
            String reasonCode = reason.split("-")[0];
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(reasonCode);
            if (refundReason.getName().startsWith(OTHERREASONPATTERN)) {
                return refundReason.getCode() + "-"
                    + refundReason.getName().substring(reasonNameLength) + "-"
                    + reason.substring(reasonPrefixLength);
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
        LOG.info("uid in isLastUpdatedByCurrentUser {}",uid);
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

            // Map UID -> User full name
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

    // DTRJ
    private Map<String, UserIdentityDataDto> getIdamUserDetails(MultiValueMap<String, String> headers, Set<String> distintUidSet) {
        Map<String, UserIdentityDataDto> userFullNameMap = new ConcurrentHashMap<>();
        for (String userId : distintUidSet) {
            UserIdentityDataDto userIdentityDataDto;
            try {
                userIdentityDataDto =
                    idamService.getUserIdentityData(headers, userId);

            } catch (UserNotFoundException userNotFoundException) {
                LOG.warn("User with UID {} not available in IdAM", userId);
                userIdentityDataDto = new UserIdentityDataDto(
                    IDAM_USER_NOT_FOUND_MSG,
                    IDAM_USER_NOT_FOUND_MSG,
                    userId,
                    Collections.<String>emptyList()
                );
            }

            userFullNameMap.put(
                userId,
                userIdentityDataDto
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
        if (null != rawReason && rawReason.startsWith("RR")) {
            List<RefundReason> refundReasonOptional = new ArrayList<>();
            for (RefundReason refundReason : refundReasonList) {
                if (refundReason.getCode().equalsIgnoreCase(rawReason)) {
                    refundReasonOptional.add(refundReason);
                    break;
                }
            }
            if (refundReasonOptional.isEmpty()) {
                return rawReason;
            } else {
                return refundReasonOptional.get(0).getName();
            }
        }
        return rawReason;
    }

    private MultiValueMap<String, String> getAuthenticationHeaders() {
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

    private String validateRefundReasonForNonRetroRemission(String reason, Refund refund) {
        if (reason == null) {
            if (refund.getReason() == null || refund.getReason().isBlank()) {
                throw new InvalidRefundRequestException("Refund reason is required");
            }
            return refund.getReason();
        } else {
            return validateRefundReason(reason);
        }
    }

    @Override
    @SuppressWarnings({"PMD.ConfusingTernary"})
    public List<RefundLiberata> search(Optional<String> startDateTimeString, Optional<String> endDateTimeString, String refundReference) {

        List<String> referenceList = new ArrayList<>();
        List<RefundLiberata> refundLiberatas = new ArrayList<>();
        List<Refund> refundListWithAccepted;
        List<Refund> refundListNotInDateRange;

        refundValidator.validate(startDateTimeString, endDateTimeString);

        Date fromDateTime = getFromDateTime(startDateTimeString);

        Date toDateTime = getToDateTime(endDateTimeString, fromDateTime);

        validateV2ApiDateRange(fromDateTime, toDateTime);

        List<Refund> refundList = refundsRepository.findAll(searchByCriteria(getSearchCriteria(
            fromDateTime,
            toDateTime,
            refundReference
        )));
        if (!refundList.isEmpty()) {
            refundListWithAccepted = refundList.stream().filter(refund -> refund.getRefundStatus().equals(
                    RefundStatus.APPROVED))
                .collect(Collectors.toList());
        } else {
            throw new RefundNotFoundException("No refunds available for the given date range");
        }

        if (!refundListWithAccepted.isEmpty()) {

            for (Refund ref : refundListWithAccepted) {
                referenceList.add(ref.getPaymentReference());
            }
        } else {
            throw new RefundNotFoundException("No refunds available for the given date range");
        }

        if (startDateTimeString.isPresent() && endDateTimeString.isPresent()) {
            refundListNotInDateRange = refundsRepository.findByDatesBetween(fromDateTime, toDateTime);
        } else {

            refundListNotInDateRange = refundsRepository.findAllByPaymentReference(
                refundListWithAccepted.get(0).getPaymentReference(),
                refundListWithAccepted.get(0).getReference()
            );
        }

        List<PaymentDto> paymentData = paymentService.fetchPaymentResponse(referenceList);

        Map<String, BigDecimal> groupByPaymentReference =
            refundListWithAccepted.stream().collect(Collectors.groupingBy(
                Refund::getPaymentReference,
                Collectors2.summingBigDecimal(Refund::getAmount)
            ));

        Map<String, BigDecimal> groupByPaymentReferenceForNotInDateRange =
            refundListNotInDateRange.stream().collect(Collectors.groupingBy(
                Refund::getPaymentReference,
                Collectors2.summingBigDecimal(Refund::getAmount)
            ));

        Map<String, BigDecimal> avlBalance;

        avlBalance = calculateAvailableBalance(groupByPaymentReference, groupByPaymentReferenceForNotInDateRange);

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
                    finalAvlBalance,toRefundReason(refund.getReason())
                ));
            });
        return refundLiberatas;
    }

    @SuppressWarnings({"PMD.UselessParentheses"})
    public Specification<Refund> searchByCriteria(RefundSearchCriteria searchCriteria) {
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
    private Map<String, BigDecimal> calculateAvailableBalance(Map<String, BigDecimal> groupByPaymentReference,
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
            avlBalance.put(key, sumAmount);
        }
        return avlBalance;
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
        for (Refund refund : refundList) {
            UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(
                headers,
                refund.getCreatedBy()
            );

            String reason = getRefundReason(refund.getReason(), refundReasonList);
            if (refund.getCreatedBy().equals(userIdentityDataDto.getId())) {
                refundListDto.add(refundResponseMapper.getRefundListDto(
                    refund,
                    userIdentityDataDto,
                    reason
                ));
            }
        }
        LOG.info("refundListDto in getRefundResponseDtoListForPaymentRole {}", refundListDto.size());
        return refundListDto;
    }

    private String toRefundReason(String reasonCode) {

        String reason;
        Optional<RefundReason> refundReason = refundReasonRepository.findByCode(reasonCode);

        if (refundReason.isPresent()) {
            reason = refundReason.get().getName();
        } else {
            reason = reasonCode.substring(REASON_CODE_END);
        }
        return  reason;
    }

    private List<RefundFees> toRefundFeeMap(Refund refund, ResubmitRefundRequest request) {
        List<RefundFees> refundFeeDtos = new ArrayList<>();

        request.getRefundFees().stream().filter(rf -> refund.getRefundFees().stream().anyMatch(
            id -> id.getFeeId().equals(rf.getFeeId()))).collect(Collectors.toList())
            .forEach(refundFromDb -> {
                refundFeeDtos.add(refundFeeMapper.toRefundFeeUpdate(refundFromDb,
                                                                    refund.getRefundFees().stream()
                                                                        .filter(rf1 -> refundFromDb.getFeeId().equals(rf1.getFeeId()))
                                                                        .findAny().get()));

            });

        refundFeeDtos.addAll(request.getRefundFees().stream().filter(rf -> refund.getRefundFees().stream()
            .noneMatch(id -> id.getFeeId().equals(rf.getFeeId()))).map(refundFeeMapper::toRefundFee).collect(Collectors.toList()));


        List<RefundFees>  refundFeeDtosNotMatched = refund.getRefundFees().stream().filter(rf -> request.getRefundFees().stream()
            .noneMatch(id -> id.getFeeId().equals(rf.getFeeId()))
        ).collect(Collectors.toList());


        List<Integer> refundFeeIds = new ArrayList<>();

        for (RefundFees id : refundFeeDtosNotMatched) {
            refundFeeIds.add(id.getId());

        }
        if (!refundFeeIds.isEmpty()) {
            deleteRefundFee(refundFeeIds);
        }


        return refundFeeDtos;

    }

    public void deleteRefundFee(List<Integer> refundFeesId) {
        refundFeesRepository.deleteByIdIn(refundFeesId);
    }


    @Override
    public RefundResponse initiateReissueRefund(String refundReference, MultiValueMap<String, String> headers,
                                                IdamUserIdResponse idamUserIdResponse) {
        try {
            Refund expiredRefund = refundsRepository.findByReferenceOrThrow(refundReference);
            validateCurrentRefund(expiredRefund);
            expiredRefund.setRefundStatus(RefundStatus.CLOSED);
            expiredRefund.setUpdatedBy(idamUserIdResponse.getUid());
            List<StatusHistory> statusHistories = new ArrayList<>(expiredRefund.getStatusHistories());
            statusHistories.add(StatusHistory.statusHistoryWith()
                                    .createdBy(idamUserIdResponse.getUid())
                                    .status(RefundStatus.CLOSED.getName())
                                    .notes(REFUND_CLOSED_BY_CASE_WORKER)
                                    .build());
            expiredRefund.setStatusHistories(statusHistories);
            LOG.info("Refund closed for reissue with reference: {}", expiredRefund.getReference());
            refundsRepository.save(expiredRefund);
            return initiateRefundProcess(expiredRefund, idamUserIdResponse);

        } catch (RefundNotFoundException | CheckDigitException exception) {
            throw new ReissueExpiredRefundException(exception.getMessage());
        } catch (RuntimeException runtimeException) {
            throw getReissueExpiredRefundException();
        }
    }

    private static ReissueExpiredRefundException getReissueExpiredRefundException() {
        return new ReissueExpiredRefundException(
            "Refund reference failed validation checks. Possible scenarios include, refund not being expired, or being closed already.");
    }

    private void validateCurrentRefund(Refund expiredRefund) {
        if (!expiredRefund.getRefundStatus().getName().equals(RefundStatus.EXPIRED.getName())) {
            throw getReissueExpiredRefundException();
        }
    }

    public RefundResponse initiateRefundProcess(Refund expiredRefund, IdamUserIdResponse idamUserIdResponse)
        throws CheckDigitException {

        List<RefundFees> copiedFees = expiredRefund.getRefundFees().stream()
            .map(fee -> {
                RefundFees newFee = new RefundFees();
                newFee.setFeeId(fee.getFeeId());
                newFee.setRefundAmount(fee.getRefundAmount());
                newFee.setCode(fee.getCode());
                newFee.setVersion(fee.getVersion());
                newFee.setVolume(fee.getVolume());
                return newFee; })
            .collect(Collectors.toList());

        Refund refund = Refund.refundsWith()
            .amount(expiredRefund.getAmount())
            .ccdCaseNumber(expiredRefund.getCcdCaseNumber())
            .paymentReference(expiredRefund.getPaymentReference())
            .reason(expiredRefund.getReason())
            .refundStatus(APPROVED)
            .refundInstructionType(expiredRefund.getRefundInstructionType())
            .notificationSentFlag(expiredRefund.getNotificationSentFlag())
            .contactDetails(expiredRefund.getContactDetails())
            .reference(referenceUtil.getNext("RF"))
            .feeIds(copiedFees.stream()
                        .map(fee -> String.valueOf(fee.getFeeId()))
                        .collect(Collectors.joining(",")))
            .serviceType(expiredRefund.getServiceType())
            .createdBy(idamUserIdResponse.getUid())
            .updatedBy(idamUserIdResponse.getUid())
            .contactDetails(expiredRefund.getContactDetails())
            .refundFees(copiedFees)
            .refundInstructionType(APPROVED.getName())
            .statusHistories(Arrays.asList(
                StatusHistory.statusHistoryWith()
                    .createdBy(idamUserIdResponse.getUid())
                    .notes(getReissueLabel(expiredRefund.getPaymentReference())
                               + " re-issue of original refund " + expiredRefund.getReference())
                    .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                    .status(REISSUED.getName()).build(),
                StatusHistory.statusHistoryWith()
                    .createdBy(idamUserIdResponse.getUid())
                    .notes(REFUND_APPROVED_BY_SYSTEM)
                    .status(APPROVED.getName())
                    .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                    .build())
            ).build();

        refundsRepository.save(refund);
        LOG.info("Reissued Refund saved");
        return RefundResponse.buildRefundResponseWith()
            .refundReference(refund.getReference())
            .build();
    }


    private String getReissueLabel(String paymentReference) {
        List<Refund> refunds = refundsRepository.findByPaymentReference(paymentReference)
            .orElse(Collections.emptyList());
        long expiredCount = refunds.stream()
            .flatMap(r -> r.getStatusHistories().stream())
            .filter(h -> RefundStatus.EXPIRED.getName().equals(h.getStatus()))
            .count();

        String suffix;
        if (expiredCount == 1) {
            suffix = "st";
        } else if (expiredCount == 2) {
            suffix = "nd";
        } else if (expiredCount == 3) {
            suffix = "rd";
        } else {
            suffix = "th";
        }
        return expiredCount + suffix;
    }

}

