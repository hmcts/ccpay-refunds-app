package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public ResponseEntity<String> resendRefundNotification(ResendNotificationRequest resendNotificationRequest,
                                                           MultiValueMap<String, String> headers) {

        validateResendNotificationRequest(resendNotificationRequest);

        NotificationType notificationType = resendNotificationRequest.getNotificationType();
        Refund refund = refundsService.getRefundForReference(resendNotificationRequest.getReference());

        ResponseEntity<String> responseEntity;
        if (notificationType.equals(EMAIL)) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                                         .email(resendNotificationRequest.getRecipientEmailAddress())
                                         .templateId(resendNotificationRequest.getTemplateId())
                                         .notificationType(EMAIL.name())
                                         .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("email_not_sent");
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .templateId(resendNotificationRequest.getTemplateId())
                .addressLine(resendNotificationRequest.getRecipientPostalAddress().getAddressLine())
                .county(resendNotificationRequest.getRecipientPostalAddress().getCounty())
                .postalCode(resendNotificationRequest.getRecipientPostalAddress().getPostalCode())
                .city(resendNotificationRequest.getRecipientPostalAddress().getCity())
                .country(resendNotificationRequest.getRecipientPostalAddress().getCountry())
                .notificationType(LETTER.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("letter_not_sent");
            RefundNotificationLetterRequest refundNotificationLetterRequestRequest = refundNotificationMapper
                .getRefundNotificationLetterRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            refund.setNotificationSentFlag("sent");
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
