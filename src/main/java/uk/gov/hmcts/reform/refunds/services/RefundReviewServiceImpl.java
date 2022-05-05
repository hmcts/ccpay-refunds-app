package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jmx.export.notification.UnableToSendNotificationException;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ForbiddenToApproveRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.mappers.ReconciliationProviderMapper;
import uk.gov.hmcts.reform.refunds.mappers.RefundReviewMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.ArrayList;
import java.util.List;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;

import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.NOTAPPLICABLE;
import static uk.gov.hmcts.reform.refunds.state.RefundState.SENTFORAPPROVAL;

@Service
public class RefundReviewServiceImpl extends StateUtil implements RefundReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundReviewServiceImpl.class);

    @Autowired
    private IdamService idamService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RefundReviewMapper refundReviewMapper;

    @Autowired
    private ReconciliationProviderService reconciliationProviderService;

    @Autowired
    private ReconciliationProviderMapper reconciliationProviderMapper;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Autowired
    RefundsUtil refundsUtil;

    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference,
                                               RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        IdamUserIdResponse userId = idamService.getUserId(headers);

        Refund refundForGivenReference = validatedAndGetRefundForGivenReference(reference,userId.getUid());

        List<StatusHistory> statusHistories = new ArrayList<>(refundForGivenReference.getStatusHistories());
        refundForGivenReference.setUpdatedBy(userId.getUid());
        statusHistories.add(StatusHistory.statusHistoryWith()
                                .createdBy(userId.getUid())
                                .status(refundReviewMapper.getStatus(refundEvent))
                                .notes(refundReviewMapper.getStatusNotes(refundEvent, refundReviewRequest))
                                .build());
        refundForGivenReference.setStatusHistories(statusHistories);
        String statusMessage = "";
        if (refundEvent.equals(RefundEvent.APPROVE)) {
            boolean isRefundLiberata = this.featureToggler.getBooleanValue("refund-liberata", false);
            if (isRefundLiberata) {
                PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                    headers,
                    refundForGivenReference.getPaymentReference()
                );
                ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper.getReconciliationProviderRequest(
                    paymentData,
                    refundForGivenReference
                );

                LOG.info("reconciliationProviderRequest: {}", reconciliationProviderRequest);
                ResponseEntity<ReconciliationProviderResponse> reconciliationProviderResponseResponse = reconciliationProviderService
                    .updateReconciliationProviderWithApprovedRefund(
                    reconciliationProviderRequest
                );
                updateRefundStatus(refundForGivenReference, refundEvent);
                updateNotification(headers, refundForGivenReference);
                
                if (reconciliationProviderResponseResponse.getStatusCode().is2xxSuccessful()) {
                    refundForGivenReference.setRefundApproveFlag("SENT");
                    refundsRepository.save(refundForGivenReference);
                } else {
                    refundForGivenReference.setRefundApproveFlag("NOT SENT");
                    refundsRepository.save(refundForGivenReference);
                    throw new ReconciliationProviderServerException("Reconciliation provider unavailable. Please try again later.");
                }
            } else {
                updateRefundStatus(refundForGivenReference, refundEvent);
                refundForGivenReference.setRefundApproveFlag("NOT SENT");
                refundsRepository.save(refundForGivenReference);
                updateNotification(headers, refundForGivenReference);
            }
            statusMessage = "Refund approved";
        }

        if (refundEvent.equals(RefundEvent.REJECT)) {
            refundForGivenReference.setContactDetails(null);

            refundForGivenReference.setNotificationSentFlag(NOTAPPLICABLE.getFlag());
            refundForGivenReference.setRefundApproveFlag(NOTAPPLICABLE.getFlag());

            updateRefundStatus(refundForGivenReference, refundEvent);
            statusMessage = "Refund rejected";
        }

        if (refundEvent.equals(RefundEvent.UPDATEREQUIRED)) {
            updateRefundStatus(refundForGivenReference, refundEvent);
            statusMessage =  "Refund returned to caseworker";
        }

        return new ResponseEntity<>(statusMessage, HttpStatus.CREATED);
    }

    private void updateNotification(MultiValueMap<String, String> headers, Refund refundForGivenReference) {
        ResponseEntity<String>  responseEntity =  sendNotification(refundForGivenReference, headers);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            refundForGivenReference.setNotificationSentFlag("SENT");
            refundForGivenReference.setContactDetails(null);
            refundsRepository.save(refundForGivenReference);
        } else if (responseEntity.getStatusCode().is5xxServerError()) {
            if (refundForGivenReference.getContactDetails().getNotificationType().equals(EMAIL.name())) {
                refundForGivenReference.setNotificationSentFlag("EMAIL_NOT_SENT");
                refundsRepository.save(refundForGivenReference);
                throw new UnableToSendNotificationException("Notification not sent ");
            } else {
                refundForGivenReference.setNotificationSentFlag("LETTER_NOT_SENT");
                refundsRepository.save(refundForGivenReference);
                throw new UnableToSendNotificationException("Notification Not sent ");
            }
        } else {
            refundForGivenReference.setNotificationSentFlag("ERROR");
            refundsRepository.save(refundForGivenReference);
            throw new UnableToSendNotificationException("Notification Not sent ");

        }

    }

    private Refund validatedAndGetRefundForGivenReference(String reference, String userId) {
        Refund refund = refundsService.getRefundForReference(reference);

        if (refund.getUpdatedBy().equals(userId)) {
            throw new ForbiddenToApproveRefundException("User cannot approve this refund.");
        }

        if (!refund.getRefundStatus().equals(SENTFORAPPROVAL.getRefundStatus())) {
            throw new InvalidRefundReviewRequestException("Refund is not submitted");
        }
        return refund;
    }

    private Refund updateRefundStatus(Refund refund, RefundEvent refundEvent) {
        RefundState updateStatusAfterAction = getRefundState(refund.getRefundStatus().getName());
        // State transition logic
        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
        refund.setRefundStatus(newState.getRefundStatus());
        return refundsRepository.save(refund);
    }

    private ResponseEntity<String> sendNotification(Refund refund,MultiValueMap<String, String> headers) {
        ResponseEntity<String> responseEntity;
        if (refund.getContactDetails().getNotificationType().equals(EMAIL.name())) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .email(refund.getContactDetails().getEmail())
                .templateId(refundsUtil.getTemplate(refund))
                .notificationType(EMAIL.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("email_not_sent");
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequestApproveJourney(refund);
            responseEntity = notificationService.postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .templateId(refundsUtil.getTemplate(refund))
                .addressLine(refund.getContactDetails().getAddressLine())
                .county(refund.getContactDetails().getCounty())
                .postalCode(refund.getContactDetails().getPostalCode())
                .city(refund.getContactDetails().getCity())
                .country(refund.getContactDetails().getCountry())
                .notificationType(LETTER.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("letter_not_sent");
            RefundNotificationLetterRequest refundNotificationLetterRequestRequest = refundNotificationMapper
                .getRefundNotificationLetterRequestApproveJourney(refund);
            responseEntity = notificationService.postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        return responseEntity;

    }


}
