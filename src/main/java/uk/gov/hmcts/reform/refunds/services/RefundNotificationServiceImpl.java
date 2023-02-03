package uk.gov.hmcts.reform.refunds.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundIdamNotificationException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.EMAILNOTSENT;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.LETTERNOTSENT;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.SENT;

@Service
public class RefundNotificationServiceImpl extends StateUtil implements RefundNotificationService {


    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private RefundsRepository refundsRepository;

    private static final Logger LOG = LoggerFactory.getLogger(RefundNotificationServiceImpl.class);

    @Autowired
    private IdamService idamService;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

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

    @Override
    public void processFailedNotificationsEmail() throws JsonProcessingException {
        String notificationSentFlag = "EMAIL_NOT_SENT";
        Optional<List<Refund>> refundList;
        LOG.info("process failed notification email fetching refund list ...");
        refundList =  refundsRepository.findByNotificationSentFlag(notificationSentFlag);
        LOG.info("process failed notification email refund list -> {}", refundList);
        List<Refund> refundListAll = new ArrayList<>();
        if (refundList.isPresent()) {
            refundListAll = refundList.get();
        }
        LOG.info("process failed notification email refund list all -> {}", refundListAll);
        refundListAll.stream().collect(Collectors.toList())
            .forEach(refund -> {

                LOG.info("Refund object : {}",  refund.toString());

                if (refund.getContactDetails().getNotificationType().equalsIgnoreCase("email")) {
                    refund.setNotificationSentFlag("EMAIL_NOT_SENT");
                    RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                        .getRefundNotificationEmailRequestApproveJourney(refund);
                    ResponseEntity<String> responseEntity;
                    LOG.info("Refund Notification Email Request {}", refundNotificationEmailRequest);
                    responseEntity =  notificationService.postEmailNotificationData(getHttpHeaders(),refundNotificationEmailRequest);
                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        refund.setNotificationSentFlag("SENT");
                        refund.setContactDetails(null);
                    }
                    refundsRepository.save(refund);
                    LOG.info("Refund notification email update saved..");
                }
            });
    }

    @Override
    public void processFailedNotificationsLetter() throws JsonProcessingException {
        String notificationSentFlag = "LETTER_NOT_SENT";
        Optional<List<Refund>> refundList;
        List<Refund> refundListAll = new ArrayList<>();
        LOG.info("process failed notification letter fetching refund list ...");
        refundList =  refundsRepository.findByNotificationSentFlag(notificationSentFlag);
        LOG.info("process failed notification letter refund list -> {}", refundList);
        if (refundList.isPresent()) {
            refundListAll = refundList.get();
        }
        LOG.info("process failed notification letter refund list all -> {}", refundListAll);
        refundListAll.stream().collect(Collectors.toList())
            .forEach(refund -> {

                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                try {
                    ow.writeValueAsString(refund);
                } catch (JsonProcessingException e) {
                    LOG.error("RJsonProcessingException. {}", e);
                }
                if (refund.getContactDetails().getNotificationType().equalsIgnoreCase("letter"))  {
                    refund.setNotificationSentFlag("LETTER_NOT_SENT");
                    RefundNotificationLetterRequest refundNotificationLetterRequest = refundNotificationMapper
                        .getRefundNotificationLetterRequestApproveJourney(refund);
                    ResponseEntity<String> responseEntity;
                    LOG.info("Refund Notification Letter Request {}", refundNotificationLetterRequest);
                    responseEntity =  notificationService.postLetterNotificationData(getHttpHeaders(),refundNotificationLetterRequest);
                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        refund.setNotificationSentFlag("SENT");
                        refund.setContactDetails(null);
                    }
                    refundsRepository.save(refund);
                    LOG.info("Refund notification letter update saved..");
                }

            });
    }

    private MultiValueMap<String,String> getHttpHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type", Arrays.asList("application/json"));
        inputHeaders.put("authorization", Arrays.asList("Bearer " + getAccessToken()));
        inputHeaders.put("ServiceAuthorization", Arrays.asList(getServiceAuthorisationToken()));
        return inputHeaders;
    }

    private String getServiceAuthorisationToken() {
        try {
            String serviceAuthToken = authTokenGenerator.generate();
            return serviceAuthToken;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new RefundIdamNotificationException("S2S", e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RefundIdamNotificationException("S2S", HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

    private String getAccessToken() {
        IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
        return idamTokenResponse.getAccessToken();
    }
}
