package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.EMAILNOTSENT;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.LETTERNOTSENT;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.SENT;

@Service
public class RefundNotificationServiceImpl implements RefundNotificationService {


    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Value("${notify.letter.template}")
    private String letterTemplateId;

    @Value("${notify.email.template}")
    private String emailTemplateId;

    @Override
    public ResponseEntity<String> resendRefundNotification(ResendNotificationRequest resendNotificationRequest,
                                                           MultiValueMap<String, String> headers) {

        Refund refund = refundsService.getRefundForReference(resendNotificationRequest.getReference());

        validateResendNotificationRequest(resendNotificationRequest);

        NotificationType notificationType = resendNotificationRequest.getNotificationType();

        ResponseEntity<String> responseEntity;
        if (notificationType.equals(EMAIL)) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                                         .email(resendNotificationRequest.getRecipientEmailAddress())
                                         .notificationType(EMAIL.name())
                                         .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag(EMAILNOTSENT.getFlag());
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .addressLine(resendNotificationRequest.getRecipientPostalAddress().getAddressLine())
                .county(resendNotificationRequest.getRecipientPostalAddress().getCounty())
                .postalCode(resendNotificationRequest.getRecipientPostalAddress().getPostalCode())
                .city(resendNotificationRequest.getRecipientPostalAddress().getCity())
                .country(resendNotificationRequest.getRecipientPostalAddress().getCountry())
                .notificationType(LETTER.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag(LETTERNOTSENT.getFlag());
            RefundNotificationLetterRequest refundNotificationLetterRequestRequest = refundNotificationMapper
                .getRefundNotificationLetterRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            refund.setNotificationSentFlag(SENT.getFlag());
            refund.setContactDetails(null);
        }
        refundsRepository.save(refund);

        return responseEntity;
    }


    private void validateResendNotificationRequest(ResendNotificationRequest resendNotificationRequest) {

        if (resendNotificationRequest.getNotificationType().equals(EMAIL)
            && resendNotificationRequest.getRecipientEmailAddress() == null) {
            throw new InvalidRefundNotificationResendRequestException("Please enter recipient email for Email notification.");
        }

        if (resendNotificationRequest.getNotificationType().equals(LETTER)
            && resendNotificationRequest.getRecipientPostalAddress() == null) {
            throw new InvalidRefundNotificationResendRequestException("Please enter recipient postal address for Postal notification.");
        }
    }
}
