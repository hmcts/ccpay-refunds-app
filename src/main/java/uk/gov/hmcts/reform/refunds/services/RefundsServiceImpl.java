package uk.gov.hmcts.reform.refunds.services;

import com.microsoft.applicationinsights.boot.dependencies.apachecommons.lang3.EnumUtils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.dtos.requests.Notification;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundResubmitPayhubRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundSearchCriteria;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
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
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundReasonNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
@SuppressWarnings({"PMD.PreserveStackTrace", "PMD.ExcessiveImports","PMD.TooManyMethods"})
public class RefundsServiceImpl extends StateUtil implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR0[0-9][0-9]-[a-zA-Z]+)");

    private static final String OTHERREASONPATTERN = "Other - ";

    private static final Pattern ROLEPATTERN = Pattern.compile("^.*refund.*$");
    private static final String RETROSPECTIVE_REMISSION_REASON = "RR036";
    private static int reasonPrefixLength = 6;
    private static final Predicate[] REF = new Predicate[0];
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

    @Autowired
    private ContextStartListener contextStartListener;

    private static final String REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER = "Refund initiated and sent to team leader";
    private static final Pattern EMAIL_ID_REGEX = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public RefundEvent[] retrieveActions(String reference) {
        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        return currentRefundState.nextValidEvents();
    }

    @Override
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException {
        //validateRefundRequest(refundRequest); //disabled this validation to allow partial refunds
        String paymnetMethod = null;
        IdamUserIdResponse uid = idamService.getUserId(headers);

        if (refundRequest.getPaymentMethod() != null) {

            if (refundRequest.getPaymentMethod().equals("cheque") || refundRequest.getPaymentMethod().equals("cash")
                || refundRequest.getPaymentMethod().equals("postal order")) {
                paymnetMethod = "RefundWhenContacted";
            } else {
                paymnetMethod = "SendRefund";
            }
        }
        Refund refund = initiateRefundEntity(refundRequest, uid.getUid(), paymnetMethod);
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
        List<String> roles = idamUserIdResponse.getRoles().stream().filter(role -> ROLEPATTERN.matcher(role).find())
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

    private void logPaymentsRefund() {
        if (contextStartListener != null) {
            LOG.info("contextStartListener is not null");
            LOG.info("contextStartListener.getUserMap(): {}", contextStartListener.getUserMap());
            if (contextStartListener.getUserMap() != null) {
                LOG.info("contextStartListener.getUserMap().get(payments-refund): {}", contextStartListener.getUserMap().get("payments-refund"));
            }
        }
    }

    public List<RefundDto> getRefundResponseDtoList(MultiValueMap<String, String> headers, List<Refund> refundList, List<String> roles) {

        //Create Refund response List
        List<RefundDto> refundListDto = new ArrayList<>();
        List<RefundReason> refundReasonList = refundReasonRepository.findAll();

        if (!roles.isEmpty()) {
            logPaymentsRefund();
            Set<UserIdentityDataDto> userIdentityDataDtoSet =  contextStartListener.getUserMap().get("payments-refund").stream().collect(
                Collectors.toSet());
            LOG.info("userIdentityDataDtoList: {}", userIdentityDataDtoSet);
            // Filter Refunds List based on Refunds Roles and Update the user full name for created by
            List<String> userIdsWithGivenRoles = userIdentityDataDtoSet.stream().map(UserIdentityDataDto::getId).collect(
                Collectors.toList());
            refundList.forEach(refund -> {
                if (!userIdsWithGivenRoles.contains(refund.getCreatedBy())) {
                    UserIdentityDataDto userIdentityDataDto = idamService.getUserIdentityData(headers,refund.getCreatedBy());
                    contextStartListener.addUserToMap("payments-refund",userIdentityDataDto);
                    userIdentityDataDtoSet.add(userIdentityDataDto);
                    userIdsWithGivenRoles.add(userIdentityDataDto.getId());
                }
            });
            refundList.stream()
                .filter(e -> userIdsWithGivenRoles.stream()
                    .anyMatch(id -> id.equals(e.getCreatedBy())))
                .collect(Collectors.toList())
                .forEach(refund -> {
                    String reason = getRefundReason(refund.getReason(), refundReasonList);
                    LOG.info("refund: {}", refund);
                    refundListDto.add(refundResponseMapper.getRefundListDto(
                        refund,
                        userIdentityDataDtoSet.stream()
                            .filter(dto -> refund.getCreatedBy().equals(dto.getId()))
                            .findAny().get(),
                        reason
                    ));
                });
        }

        LOG.info("refundListDto: {}", refundListDto);
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
        return rejectionReasonRepository.findAll().stream().map(reason -> RejectionReasonResponse.rejectionReasonWith()
            .code(reason.getCode())
            .name(reason.getName())
            .build()
        )
            .collect(Collectors.toList());
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

            BigDecimal refundAmount = request.getAmount() == null ? refund.getAmount() : request.getAmount();

            if (!(refund.getReason().equals(RETROSPECTIVE_REMISSION_REASON)) && !(RETROSPECTIVE_REMISSION_REASON.equals(refundReason))) {
                refund.setReason(refundReason);
            }
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
            Set<String> distintUidSet = statusHistories
                .stream().map(StatusHistory::getCreatedBy)
                .collect(Collectors.toSet());

            //Map UID -> User full name
            Map<String, UserIdentityDataDto> userFullNameMap = getIdamUserDetails(headers, distintUidSet);

            statusHistories
                .forEach(statusHistory ->
                             statusHistoryDtos.add(statusHistoryResponseMapper.getStatusHistoryDto(
                                 statusHistory,
                                 userFullNameMap.get(statusHistory.getCreatedBy())
                             )));
        }
        return StatusHistoryResponseDto.statusHistoryResponseDtoWith()
            .lastUpdatedByCurrentUser(isUpdatedByCurrentUser)
            .statusHistoryDtoList(statusHistoryDtos)
            .build();
    }

    private Map<String, UserIdentityDataDto> getIdamUserDetails(MultiValueMap<String, String> headers, Set<String> distintUidSet) {
        Map<String, UserIdentityDataDto> userFullNameMap = new ConcurrentHashMap<>();
        distintUidSet.forEach(userId -> userFullNameMap.put(
            userId,
            idamService.getUserIdentityData(headers, userId)
        ));
        return userFullNameMap;
    }

    private void validateRefundRequest(RefundRequest refundRequest) {

        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(refundRequest.getPaymentReference());

        if (refundsList.isPresent()) {
            List<String> nonRejectedFeeList = refundsList.get().stream().filter(refund -> !refund.getRefundStatus().equals(
                RefundStatus.REJECTED))
                .map(Refund::getFeeIds)
                .collect(Collectors.toList());

            List<String> feeIdsofRequestedRefund = refundRequest.getFeeIds().contains(",") ? Arrays.stream(refundRequest.getFeeIds().split(
                ",")).collect(Collectors.toList()) : Arrays.asList(refundRequest.getFeeIds());

            feeIdsofRequestedRefund.forEach(feeId -> {
                nonRejectedFeeList.forEach(nonRejectFee -> {
                    if (nonRejectFee.contains(feeId)) {
                        throw new InvalidRefundRequestException("Refund is already requested for this payment");
                    }
                });

            });
        }

        refundRequest.setRefundReason(validateRefundReason(refundRequest.getRefundReason()));

    }

    private Refund initiateRefundEntity(RefundRequest refundRequest, String uid, String paymentMethod) throws CheckDigitException {
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
            .statusHistories(
                Arrays.asList(StatusHistory.statusHistoryWith()
                                  .createdBy(uid)
                                  .notes(REFUND_INITIATED_AND_SENT_TO_TEAM_LEADER)
                                  .status(SENTFORAPPROVAL.getName())
                                  .build()
                )
            )
            .refundInstructionType(paymentMethod)

            .build();
    }

    private String getRefundReason(String rawReason, List<RefundReason> refundReasonList) {
        if (null != rawReason && rawReason.startsWith("RR")) {
            List<RefundReason> refundReasonOptional =  refundReasonList.stream()
                .filter(refundReason -> refundReason.getCode().equalsIgnoreCase(rawReason))
                .collect(Collectors.toList());
            if (!refundReasonOptional.isEmpty()) {
                return refundReasonOptional.get(0).getName();
            }
            throw new RefundReasonNotFoundException(rawReason);
        }
        return rawReason;
    }

    @Override
    @SuppressWarnings({"PMD.ConfusingTernary"})
    public List<RefundLiberata> search(RefundSearchCriteria searchCriteria) {

        List<String> reference =  new ArrayList<>();
        List<RefundLiberata> refundLiberatas = new ArrayList<>();
        List<Refund> refundListWithAccepted;

        List<Refund> refundList = refundsRepository.findAll(searchByCriteria(searchCriteria));
        if (!refundList.isEmpty()) {
            refundListWithAccepted = refundList.stream().filter(refund -> refund.getRefundStatus().equals(
                    RefundStatus.ACCEPTED))
                .collect(Collectors.toList());
            for (Refund ref : refundListWithAccepted) {
                reference.add(ref.getPaymentReference());
            }
        } else {
            throw new RefundNotFoundException("No refunds available for the given date range");
        }

        List<PaymentDto> paymentData =  paymentService.fetchPaymentResponse(reference);

        refundListWithAccepted.stream()
            .filter(e -> paymentData.stream()
                .anyMatch(id -> id.getReference().equals(e.getPaymentReference())))
            .collect(Collectors.toList())
            .forEach(refund -> {
                LOG.info("refund: {}", refund);
                refundLiberatas.add(refundResponseMapper.getRefundLibrata(
                    refund,
                    paymentData.stream()
                        .filter(dto -> refund.getPaymentReference().equals(dto.getReference()))
                        .findAny().get()
                ));
            });
        return refundLiberatas;
    }

    @SuppressWarnings({"PMD.UselessParentheses"})
    public  Specification<Refund> searchByCriteria(RefundSearchCriteria searchCriteria) {
        return ((root, query, cb) -> getPredicate(root, cb, searchCriteria, query));
    }

    private static Predicate getPredicate(
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
}
