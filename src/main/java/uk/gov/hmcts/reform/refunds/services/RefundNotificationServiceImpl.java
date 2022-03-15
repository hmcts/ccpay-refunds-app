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
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundIdamNotificationException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.mappers.ReconciliationProviderMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;
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

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReconciliationProviderService reconciliationProviderService;

    @Autowired
    private ReconciliationProviderMapper reconciliationProviderMapper;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private RefundsUtil refundsUtil;

    @Override
    public ResponseEntity<String> resendRefundNotification(ResendNotificationRequest resendNotificationRequest,
                                                           MultiValueMap<String, String> headers) {

        Refund refund = refundsService.getRefundForReference(resendNotificationRequest.getReference());

        validateResendNotificationRequest(resendNotificationRequest);

        NotificationType notificationType = resendNotificationRequest.getNotificationType();

        String templateId = findTemplateId(resendNotificationRequest, headers);

        ResponseEntity<String> responseEntity;
        if (notificationType.equals(EMAIL)) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                                         .email(resendNotificationRequest.getRecipientEmailAddress())
                                         .notificationType(EMAIL.name())
                                         .templateId(templateId)
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
                .templateId(templateId)
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

    private String findTemplateId(ResendNotificationRequest resendNotificationRequest, MultiValueMap<String, String> headers) {

        PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                headers,
                resendNotificationRequest.getReference()
        );

        String method = null;
        for (PaymentResponse paymentResponse : paymentData.getPayments()) {
            if (resendNotificationRequest.getReference().equals(paymentResponse.getReference())) {
                method = paymentResponse.getMethod();
            }
        }
        return refundsUtil.getTemplate(method, resendNotificationRequest.getNotificationType().name());

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
        refundList =  refundsRepository.findByNotificationSentFlag(notificationSentFlag);
        List<Refund> refundListAll = new ArrayList<>();
        if (refundList.isPresent()) {
            refundListAll = refundList.get();
        }
        refundListAll.stream().collect(Collectors.toList())
            .forEach(refund -> {

                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                String refundsDto = null;
                try {
                    refundsDto = ow.writeValueAsString(refund);
                } catch (JsonProcessingException e) {
                    LOG.error("JsonProcessingException : {}",  e);
                }
                LOG.info("Refund object : {}",  refundsDto);

                if (refund.getContactDetails().getNotificationType().equalsIgnoreCase("email")) {
                    refund.setNotificationSentFlag("EMAIL_NOT_SENT");
                    RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                        .getRefundNotificationEmailRequestApproveJourney(refund);
                    ResponseEntity<String> responseEntity;
                    responseEntity =  notificationService.postEmailNotificationData(getHttpHeaders(),refundNotificationEmailRequest);
                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        refund.setNotificationSentFlag("SENT");
                        refund.setContactDetails(null);
                    }
                    refundsRepository.save(refund);
                }
            });
    }

    @Override
    public void processFailedNotificationsLetter() throws JsonProcessingException {
        String notificationSentFlag = "LETTER_NOT_SENT";
        Optional<List<Refund>> refundList;
        List<Refund> refundListAll = new ArrayList<>();
        refundList =  refundsRepository.findByNotificationSentFlag(notificationSentFlag);
        if (refundList.isPresent()) {
            refundListAll = refundList.get();
        }
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
                    responseEntity =  notificationService.postLetterNotificationData(getHttpHeaders(),refundNotificationLetterRequest);
                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        refund.setNotificationSentFlag("SENT");
                        refund.setContactDetails(null);
                    }
                    refundsRepository.save(refund);
                }

            });
    }

    @Override
    public void reprocessPostFailedRefundsToLiberata() throws JsonProcessingException {

        String liberataSentFlag = "NOT_SENT";
        Optional<List<Refund>> refundList;
        String refundStatus = "Approved";
        List<Refund> refundListAll = new ArrayList<>();
        refundList =  refundsRepository.findByRefundStatusAndRefundApproveFlag(refundStatus, liberataSentFlag);
        if (refundList.isPresent()) {
            refundListAll = refundList.get();
        }

        refundListAll.stream().collect(Collectors.toList())
            .forEach(refund -> {

                boolean isRefundLiberata = this.featureToggler.getBooleanValue("refund-liberata", false);
                if (isRefundLiberata) {

                    PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                        getHttpHeaders(),
                        refund.getPaymentReference()
                    );
                    ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper.getReconciliationProviderRequest(
                        paymentData,
                        refund
                    );
                    ResponseEntity<ReconciliationProviderResponse> reconciliationProviderResponseResponse = reconciliationProviderService
                        .updateReconciliationProviderWithApprovedRefund(
                            reconciliationProviderRequest
                        );
                    if (reconciliationProviderResponseResponse.getStatusCode().is2xxSuccessful()) {
                        ReviewerAction reviewerAction = ReviewerAction.APPROVE;
                        RefundEvent refundEvent = reviewerAction.getEvent();
                        RefundState updateStatusAfterAction = getRefundState(refund.getRefundStatus().getName());
                        // State transition logic
                        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
                        refund.setRefundStatus(newState.getRefundStatus());
                        refund.setRefundApproveFlag("SENT");
                        refundsRepository.save(refund);
                    } else {

                        LOG.info("Reconciliation provider unavailable. Please try again later.");
                    }

                }
            });

    }



    private MultiValueMap<String,String> getHttpHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("Content-Type", Arrays.asList("application/json"));
        inputHeaders.put("Authorization", Arrays.asList("Bearer " + getAccessToken()));
        inputHeaders.put("ServiceAuthorization", Arrays.asList(getServiceAuthorisationToken()));
        LOG.info("HttpHeader {}", inputHeaders);
        return inputHeaders;
    }

    private String getServiceAuthorisationToken() {
        try {
            String serviceAuthToken = authTokenGenerator.generate();
            LOG.info("authTokenGenerator.generate() {}",serviceAuthToken);
            return serviceAuthToken;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new RefundIdamNotificationException("S2S", e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RefundIdamNotificationException("S2S", HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

    private String getAccessToken() {
        IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
        LOG.info("idamTokenResponse {}",idamTokenResponse.getAccessToken());
        return idamTokenResponse.getAccessToken();
    }
}
