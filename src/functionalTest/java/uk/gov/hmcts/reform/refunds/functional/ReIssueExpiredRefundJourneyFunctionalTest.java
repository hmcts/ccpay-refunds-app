package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.functional.config.CcdService;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.functional.config.ValidUser;
import uk.gov.hmcts.reform.refunds.functional.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.functional.request.*;
import uk.gov.hmcts.reform.refunds.functional.response.PaymentDto;
import uk.gov.hmcts.reform.refunds.functional.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.functional.service.PaymentTestService;
import uk.gov.hmcts.reform.refunds.functional.util.DataGenerator;
import uk.gov.hmcts.reform.refunds.functional.util.NotifyUtil;
import uk.gov.hmcts.reform.refunds.functional.util.PaymentMethodType;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
//import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ActiveProfiles("functional")
@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest
public class ReIssueExpiredRefundJourneyFunctionalTest {
    @Autowired
    private TestConfigProperties testConfigProperties;

    @Autowired
    private IdamService idamService;

    @Autowired
    private S2sTokenService s2sTokenService;

    @Autowired
    private CcdService ccdService;

    @Inject
    private PaymentTestService paymentTestService;
    @Autowired
    private RefundsRepository refundsRepository;
    @Autowired
    private DataGenerator dataGenerator;
    @Autowired
    private NotifyUtil notifyUtil;

    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR;
    private static String USER_ID_PROBATE_DRAFT_CASE_CREATOR;
    private static String SERVICE_TOKEN_CMC;
    private static boolean TOKENS_INITIALIZED;
    private static List<String> userEmails = new ArrayList<>();
    private List<String> paymentReferences = new ArrayList<>();
    private List<String> refundReferences = new ArrayList<>();
    private static final Pattern
        REFUNDS_REGEX_PATTERN = Pattern.compile("^(RF)-([0-9]{4})-([0-9-]{4})-([0-9-]{4})-([0-9-]{4})$");
    private static final String DATE_TIME_FORMAT_T_HH_MM_SS = "yyyy-MM-dd'T'HH:mm:ss";
    private static final int CCD_EIGHT_DIGIT_UPPER = 99999999;
    private static final int CCD_EIGHT_DIGIT_LOWER = 10000000;
    private static final Logger LOG = LoggerFactory.getLogger(IdamService.class);

    @Before
    public void setUp() {

        RestAssured.baseURI = testConfigProperties.baseTestUrl;
        if (!TOKENS_INITIALIZED) {
            ValidUser user1 = idamService.createUserWith("caseworker-cmc-solicitor");
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE = user1.getAuthorisationToken();
            userEmails.add(user1.getEmail());

            ValidUser user2 = idamService.createUserWithSearchScope(
                "payments-refund",
                "payments-refund-divorce",
                "payments-refund-probate"
            );
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE = user2.getAuthorisationToken();
            userEmails.add(user2.getEmail());

            ValidUser user3 = idamService.createUserWithSearchScope("payments-refund-approver",
                                                                    "payments-refund-approver-divorce",
                                                                    "payments-refund-approver-probate"
            );
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE = user3.getAuthorisationToken();
            userEmails.add(user3.getEmail());

            ValidUser user4 = idamService.createUserWithSearchScope("payments-refund");
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE = user4.getAuthorisationToken();
            userEmails.add(user4.getEmail());

            ValidUser user5 = idamService.createUserWithSearchScope("payments-refund-approver");
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE = user5.getAuthorisationToken();
            userEmails.add(user5.getEmail());

            ValidUser user6 = idamService.createUserWithSearchScope("payments");
            USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE = user6.getAuthorisationToken();
            userEmails.add(user6.getEmail());

            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR = idamService.authenticateUser(
                testConfigProperties.getProbateCaseworkerUsername(),
                testConfigProperties.getProbateCaseworkerPassword()
            );
            USER_ID_PROBATE_DRAFT_CASE_CREATOR = idamService.getUserDetails(USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR);

            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);

            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);

            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void positive_reissue_expired_refund_test() throws NotificationClientException {
        String emailAddress = dataGenerator.generateEmail(16);
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, emailAddress);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        // Verify notification email content for initial Refund Accepted
        Notification notification1 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        String emailSubject = notifyUtil.getNotifyEmailSubject(notification1);
        String emailBody1 = notifyUtil.getNotifyEmailBody(notification1);

        assertEquals("HMCTS refund request approved", emailSubject,
                     "Email subject does not match for initial Refund Accepted");
        assertTrue(emailBody1.contains("Refund reference: " + refundReference),
                   "Email body does not contain the refund reference");
        assertTrue(emailBody1.contains("Your refund will be processed and sent to the account you originally made the payment from within 14 days"),
                   "Email body does not contain expected text for initial Refund Accepted");

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Verify notification email content for Refund Accepted after Rejected
        Notification notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        if (notification2.getId().equals(notification1.getId())) {
            // try again
            try {
                Thread.sleep(2000); // wait for 2 seconds before retrying
                LOG.info("Retrying to fetch notification email for {}", emailAddress);
                notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotEquals(notification2.getId(), notification1.getId(), "New notification not sent"); //ensure it's a new notification

        String emailBody2 = notifyUtil.getNotifyEmailBody(notification2);
        assertTrue(emailBody2.contains("Refund reference: " + refundReference),
                   "Email body does not contain the refund reference");
        assertTrue(emailBody2.contains("Unfortunately, our attempt to refund the payment card that you used was declined by your card provider."),
                   "Email body does not contain expected text for Refund Rejected");

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference);

        // verify that old refund status changed to Closed
        RefundDto refundDto7 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto7.getRefundStatus());
        assertEquals("Amended claim", refundDto7.getReason());

        // verify that new refund status changed to Approved immediately after Ressiued and Reason remain same
        // Reissued status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto8 = getRefundListByCaseNumberAndStatus(ccdCaseNumber, newRefundReference, "Approved");
        assertEquals(RefundStatus.APPROVED, refundDto8.getRefundStatus());
        assertEquals("Amended claim", refundDto8.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse4 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse4.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto9 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto9.getRefundStatus());
        assertEquals("Amended claim", refundDto9.getReason());

        // Verify notification email content for Refund Accepted after Reissued
        Notification notification3 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        if (notification3.getId().equals(notification2.getId())) {
            // try again
            try {
                Thread.sleep(2000); // wait for 2 seconds before retrying
                LOG.info("Retrying to fetch notification email for {}", emailAddress);
                notification3 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotEquals(notification3.getId(), notification2.getId(), "New notification not sent"); //ensure it's a new notification

        String emailBody3 = notifyUtil.getNotifyEmailBody(notification3);
        assertTrue(emailBody3.contains("Refund reference: " + newRefundReference),
                   "Email body does not contain new refund reference");
        assertTrue(emailBody3.contains("Unfortunately, our attempt to refund the payment card that you used was declined by your card provider."),
                   "Email body does not contain expected text for Refund Rejected");

        //verify the old refund status history
        Response oldRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(oldRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> oldRefundStatusHistoryList =
            oldRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(8, oldRefundStatusHistoryList.size());

        assertEquals("Closed", oldRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Refund closed by case worker", oldRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Expired", oldRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Unable to process expired refund", oldRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Accepted", oldRefundStatusHistoryList.get(2).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", oldRefundStatusHistoryList.get(2).get("notes").trim());

        assertEquals("Approved", oldRefundStatusHistoryList.get(3).get("status").trim());
        assertEquals("Refund approved by system", oldRefundStatusHistoryList.get(3).get("notes").trim());

        assertEquals("Rejected", oldRefundStatusHistoryList.get(4).get("status").trim());
        assertEquals("Unable to apply refund to Card", oldRefundStatusHistoryList.get(4).get("notes").trim());

        assertEquals("Accepted", oldRefundStatusHistoryList.get(5).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", oldRefundStatusHistoryList.get(5).get("notes").trim());

        assertEquals("Approved", oldRefundStatusHistoryList.get(6).get("status").trim());
        assertEquals("Sent to middle office", oldRefundStatusHistoryList.get(6).get("notes").trim());

        assertEquals("Sent for approval", oldRefundStatusHistoryList.get(7).get("status").trim());
        assertEquals("Refund initiated and sent to team leader", oldRefundStatusHistoryList.get(7).get("notes").trim());

        //verify the new refund status history
        Response newRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference
            );
        assertThat(newRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList =
            newRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(3, newRefundStatusHistoryList.size());

        assertEquals("Accepted", newRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", newRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Approved", newRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList.get(2).get("status").trim());
        assertEquals("1st re-issue of original refund " + refundReference, newRefundStatusHistoryList.get(2).get("notes").trim());
    }

    @Test
    public void negative_reissue_not_expired_refund_should_return_400_bad_request() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertEquals("There was a problem processing the supplied refund reference.", reissueExpiredRefundResponse.getBody().asString());
    }

    @Test
    public void negative_reissue_closed_refund_should_return_400_bad_request() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference);

        // verify that old refund status changed to Closed
        RefundDto refundDto7 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto7.getRefundStatus());
        assertEquals("Amended claim", refundDto7.getReason());

        // Reissue the closed refund again
        Response reissueClosedRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueClosedRefundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertEquals("There was a problem processing the supplied refund reference.", reissueClosedRefundResponse.getBody().asString());
    }

    @Test
    public void negative_reissue_expired_refund_without_valid_access_token_should_return_401_unauthorised() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            "dhdhdhddhjwihjdswdhdwd",
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(UNAUTHORIZED.value());
    }

    @Test
    public void negative_reissue_expired_refund_without_valid_service_token_should_return_401_unauthorised() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            "asxjxhasdjhsdhi",
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(UNAUTHORIZED.value());
    }

    @Test
    public void negative_reissue_expired_refund_without_refund_roles_should_return_403_forbidden() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(FORBIDDEN.value());
    }

    @Test
    public void negative_reissue_expired_refund_without_service_refund_role_should_return_409_conflict() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund with the refund requester role but without service refund role
        Response reissueExpiredRefundResponse1 = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse1.getStatusCode()).isEqualTo(CONFLICT.value());
        assertEquals("Action not allowed to user for given service name", reissueExpiredRefundResponse1.getBody().asString());

        // Reissue the expired refund with the refund approver role but without service refund role
        Response reissueExpiredRefundResponse2 = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse2.getStatusCode()).isEqualTo(CONFLICT.value());
        assertEquals("Action not allowed to user for given service name", reissueExpiredRefundResponse2.getBody().asString());
    }

    @Test
    public void negative_reissue_non_existing_refund_should_return_404_not_found() {
        final String refundReference = "RF-2025-0000-0000-0000";

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(NOT_FOUND.value());
        assertThat(reissueExpiredRefundResponse.getBody().asString()).isEqualTo("Refund not found for given reference");
    }

    @Test
    public void negative_reissue_refund_with_invalid_format_should_return_400_bad_request() {
        final String refundReference = "RF-2025-0000-0000-000";

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertThat(reissueExpiredRefundResponse.getBody().asString())
            .isEqualTo(String.format("{\"reissueExpired.reference\":\"The value %s not correctly formatted.\"}", refundReference)
            );
    }

    @Test
    public void positive_new_refund_for_remaining_fee_amount_allowed_after_other_refunds_reissued() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount1 = "90.00";
        final String refundAmount2 = "210.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount1, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference1 = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference1).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference1);

        // verify that old refund status changed to Closed
        RefundDto refundDto7 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto7.getRefundStatus());
        assertEquals("Amended claim", refundDto7.getReason());

        // verify that new refund status changed to Approved immediately after Ressiued and Reason remain same
        // Reissued status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto8 = getRefundListByCaseNumberAndStatus(ccdCaseNumber, newRefundReference1, "Approved");
        assertEquals(RefundStatus.APPROVED, refundDto8.getRefundStatus());
        assertEquals("Amended claim", refundDto8.getReason());

        //verify the 1st re-issued refund status history
        Response newRefundStatusHistoryListResponse1 =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference1
            );
        assertThat(newRefundStatusHistoryListResponse1.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList1 =
            newRefundStatusHistoryListResponse1.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(2, newRefundStatusHistoryList1.size());
        assertEquals("Approved", newRefundStatusHistoryList1.get(0).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList1.get(0).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList1.get(1).get("status").trim());
        assertEquals("1st re-issue of original refund " + refundReference, newRefundStatusHistoryList1.get(1).get("notes").trim());

        // Liberata Accepted the 1s1 re-issued Refund
        Response updateRefundStatusResponse4 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference1,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse4.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto9 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference1);
        assertEquals(RefundStatus.ACCEPTED, refundDto9.getRefundStatus());
        assertEquals("Amended claim", refundDto9.getReason());

        // Liberata expired the 1st re-issued refund after 21 days
        Response updateRefundStatusResponse5 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference1,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse5.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto10 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference1);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto10.getReason());

        // Reissue the 1st re-issued expired refund
        Response reissueExpiredRefundResponse2 = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference1);
        assertThat(reissueExpiredRefundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference2 = reissueExpiredRefundResponse2.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference2).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference2);

        //verify the 2nd re-issued refund status history
        Response newRefundStatusHistoryListResponse2 =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference2
            );
        assertThat(newRefundStatusHistoryListResponse2.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList2 =
            newRefundStatusHistoryListResponse2.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(2, newRefundStatusHistoryList2.size());
        assertEquals("Approved", newRefundStatusHistoryList2.get(0).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList2.get(0).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList2.get(1).get("status").trim());
        assertEquals("2nd re-issue of original refund " + refundReference, newRefundStatusHistoryList2.get(1).get("notes").trim());

        // Can create a new refund for remaining fee amount after other refunds re-issued
        final String refundReference2 = performRefund(refundReason, paymentReference, refundAmount2, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference2);
    }

    @Test
    public void positive_new_refund_for_remaining_fee_amount_allowed_after_expired_refund() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount1 = "90.00";
        final String refundAmount2 = "210.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount1, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Can create a new refund for remaining fee amount after other refunds re-issued
        final String refundReference2 = performRefund(refundReason, paymentReference, refundAmount2, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference2);
    }

    @Test
    public void negative_new_refund_with_more_than_the_remaining_fee_amount_not_allowed_after_expired_refund() {
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String feeAmount = "300.00";
        final String refundAmount = "200.00";
        final String remainingFeeRefundAmount = "100.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion);
        refundReferences.add(refundReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());


        // verify that status changed to Approved immediately after Rejected
        // Rejected status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status change to Accepted
        RefundDto refundDto5 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Can not create a new refund for more than the remaining fee amount after other refund expired
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertEquals(String.format("The amount to refund can not be more than £%s", remainingFeeRefundAmount), refundResponse.getBody().asString());
    }

    @Test
    public void positive_reissue_refund_for_cheque_payment() throws Exception {
        String emailAddress = dataGenerator.generateEmail(16);
        final String service = "Probate";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";
        final String paymentMethod = "cheque";
        final Integer bankGiroSlipNo = 965556;

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        String[] dcn = {"6600000000001" + RandomUtils.nextInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER)};

        BulkScanDcnPayment bulkScanDcnPayment = createBulkScanDcnPayment(
            new BigDecimal(feeAmount),
            bankGiroSlipNo,
            LocalDate.now().toString(),
            "GBP",
            dcn[0],
            paymentMethod
        );
        Response bulkScanDcnPaymentResponse = paymentTestService.postBulkScanDcnPayment(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanDcnPayment
        );
        bulkScanDcnPaymentResponse.then().statusCode(CREATED.value()).and().toString().equals("created");

        BulkScanCcdPayment bulkScanCcdPayment = createBulkScanCcdPayments(ccdCaseNumber,
                                                                          dcn,
                                                                          "AA08",
                                                                          false);
        Response bulkScanCcdPaymentsResponse = paymentTestService.postBulkScanCcdPayments(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanCcdPayment
        );
        bulkScanCcdPaymentsResponse.then().statusCode(CREATED.value()).body(
            "payment_dcns",
            equalTo(Arrays.asList(dcn))
        );

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(feeAmount))
            .service(service)
            .siteId("AA08")
            .currency("GBP")
            .documentControlNumber(dcn[0])
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User1")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.CHEQUE)
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentProvider("exela")
            .giroSlipNo(bankGiroSlipNo.toString())
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                                    .calculatedAmount(new BigDecimal(feeAmount))
                                    .code(feeCode)
                                    .version(feeVersion)
                                    .reference("testRef1")
                                    .volume(1)
                                    .ccdCaseNumber(ccdCaseNumber)
                                    .build())).build();

        Response paymentGroupResponse = paymentTestService.addNewFeeAndPaymentGroup(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                    SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                    testConfigProperties.basePaymentsUrl,
                                                                                    paymentGroupDto
        );

        assertThat(paymentGroupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        String paymentGroupReference = paymentGroupResponse.getBody().jsonPath().getString("payment_group_reference");

        Response paymentResponse = paymentTestService.createBulkScanPayment(
            USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.basePaymentsUrl,
            bulkScanPaymentRequest,
            paymentGroupReference
        );

        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String paymentReference = paymentResponse.getBody().jsonPath().getString("reference");

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "20",
                                                                              testConfigProperties.basePaymentsUrl
        );

        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, emailAddress);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        // Verify notification email content for Refund Accepted after Rejected
        Notification notification1 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        String emailBody1 = notifyUtil.getNotifyEmailBody(notification1);
        assertTrue(emailBody1.contains("Refund reference: " + refundReference),
                   "Email body does not contain the refund reference");
        assertTrue(emailBody1.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto3 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto3.getRefundStatus());
        assertEquals("Amended claim", refundDto3.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference);

        // verify that old refund status changed to Closed
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // verify that new refund status changed to Approved immediately after Ressiued and Reason remain same
        // Reissued status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto5 = getRefundListByCaseNumberAndStatus(ccdCaseNumber, newRefundReference, "Approved");
        assertEquals(RefundStatus.APPROVED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Verify notification email content for Refund Accepted after Reissued
        Notification notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        if (notification2.getId().equals(notification1.getId())) {
            // try again
            try {
                Thread.sleep(2000); // wait for 2 seconds before retrying
                LOG.info("Retrying to fetch notification email for {}", emailAddress);
                notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotEquals(notification2.getId(), notification2.getId(), "New notification not sent"); //ensure it's a new notification

        String emailBody2 = notifyUtil.getNotifyEmailBody(notification2);
        assertTrue(emailBody2.contains("Refund reference: " + newRefundReference),
                   "Email body does not contain new refund reference");
        assertTrue(emailBody2.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        //verify the old refund status history
        Response oldRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(oldRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> oldRefundStatusHistoryList =
            oldRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(8, oldRefundStatusHistoryList.size());

        assertEquals("Closed", oldRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Refund closed by case worker", oldRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Expired", oldRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Unable to process expired refund", oldRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Accepted", oldRefundStatusHistoryList.get(5).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", oldRefundStatusHistoryList.get(5).get("notes").trim());

        assertEquals("Approved", oldRefundStatusHistoryList.get(6).get("status").trim());
        assertEquals("Sent to middle office", oldRefundStatusHistoryList.get(6).get("notes").trim());

        assertEquals("Sent for approval", oldRefundStatusHistoryList.get(7).get("status").trim());
        assertEquals("Refund initiated and sent to team leader", oldRefundStatusHistoryList.get(7).get("notes").trim());

        //verify the new refund status history
        Response newRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference
            );
        assertThat(newRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList =
            newRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(3, newRefundStatusHistoryList.size());

        assertEquals("Accepted", newRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", newRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Approved", newRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList.get(2).get("status").trim());
        assertEquals("1st re-issue of original refund " + refundReference, newRefundStatusHistoryList.get(2).get("notes").trim());
    }

    @Test
    public void positive_reissue_refund_for_cash_payment() throws Exception {
        String emailAddress = dataGenerator.generateEmail(16);
        final String service = "Probate";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";
        final String paymentMethod = "cash";
        final Integer bankGiroSlipNo = 965556;

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        String[] dcn = {"6600000000001" + RandomUtils.nextInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER)};

        BulkScanDcnPayment bulkScanDcnPayment = createBulkScanDcnPayment(
            new BigDecimal(feeAmount),
            bankGiroSlipNo,
            LocalDate.now().toString(),
            "GBP",
            dcn[0],
            paymentMethod
        );
        Response bulkScanDcnPaymentResponse = paymentTestService.postBulkScanDcnPayment(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanDcnPayment
        );
        bulkScanDcnPaymentResponse.then().statusCode(CREATED.value()).and().toString().equals("created");

        BulkScanCcdPayment bulkScanCcdPayment = createBulkScanCcdPayments(ccdCaseNumber,
                                                                          dcn,
                                                                          "AA08",
                                                                          false);
        Response bulkScanCcdPaymentsResponse = paymentTestService.postBulkScanCcdPayments(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanCcdPayment
        );
        bulkScanCcdPaymentsResponse.then().statusCode(CREATED.value()).body(
            "payment_dcns",
            equalTo(Arrays.asList(dcn))
        );

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(feeAmount))
            .service(service)
            .siteId("AA08")
            .currency("GBP")
            .documentControlNumber(dcn[0])
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User1")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.CASH)
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentProvider("exela")
            .giroSlipNo(bankGiroSlipNo.toString())
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                                    .calculatedAmount(new BigDecimal(feeAmount))
                                    .code(feeCode)
                                    .version(feeVersion)
                                    .reference("testRef1")
                                    .volume(1)
                                    .ccdCaseNumber(ccdCaseNumber)
                                    .build())).build();

        Response paymentGroupResponse = paymentTestService.addNewFeeAndPaymentGroup(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                    SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                    testConfigProperties.basePaymentsUrl,
                                                                                    paymentGroupDto
        );

        assertThat(paymentGroupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        String paymentGroupReference = paymentGroupResponse.getBody().jsonPath().getString("payment_group_reference");

        Response paymentResponse = paymentTestService.createBulkScanPayment(
            USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.basePaymentsUrl,
            bulkScanPaymentRequest,
            paymentGroupReference
        );

        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String paymentReference = paymentResponse.getBody().jsonPath().getString("reference");

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "20",
                                                                              testConfigProperties.basePaymentsUrl
        );

        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, emailAddress);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        // Verify notification email content for Refund Accepted after Rejected
        Notification notification1 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        String emailBody1 = notifyUtil.getNotifyEmailBody(notification1);
        assertTrue(emailBody1.contains("Refund reference: " + refundReference),
                   "Email body does not contain the refund reference");
        assertTrue(emailBody1.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto3 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto3.getRefundStatus());
        assertEquals("Amended claim", refundDto3.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference);

        // verify that old refund status changed to Closed
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // verify that new refund status changed to Approved immediately after Ressiued and Reason remain same
        // Reissued status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto5 = getRefundListByCaseNumberAndStatus(ccdCaseNumber, newRefundReference, "Approved");
        assertEquals(RefundStatus.APPROVED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Verify notification email content for Refund Accepted after Reissued
        Notification notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        if (notification2.getId().equals(notification1.getId())) {
            // try again
            try {
                Thread.sleep(2000); // wait for 2 seconds before retrying
                LOG.info("Retrying to fetch notification email for {}", emailAddress);
                notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotEquals(notification2.getId(), notification2.getId(), "New notification not sent"); //ensure it's a new notification

        String emailBody2 = notifyUtil.getNotifyEmailBody(notification2);
        assertTrue(emailBody2.contains("Refund reference: " + newRefundReference),
                   "Email body does not contain new refund reference");
        assertTrue(emailBody2.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        //verify the old refund status history
        Response oldRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(oldRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> oldRefundStatusHistoryList =
            oldRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(8, oldRefundStatusHistoryList.size());

        assertEquals("Closed", oldRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Refund closed by case worker", oldRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Expired", oldRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Unable to process expired refund", oldRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Accepted", oldRefundStatusHistoryList.get(5).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", oldRefundStatusHistoryList.get(5).get("notes").trim());

        assertEquals("Approved", oldRefundStatusHistoryList.get(6).get("status").trim());
        assertEquals("Sent to middle office", oldRefundStatusHistoryList.get(6).get("notes").trim());

        assertEquals("Sent for approval", oldRefundStatusHistoryList.get(7).get("status").trim());
        assertEquals("Refund initiated and sent to team leader", oldRefundStatusHistoryList.get(7).get("notes").trim());

        //verify the new refund status history
        Response newRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference
            );
        assertThat(newRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList =
            newRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(3, newRefundStatusHistoryList.size());

        assertEquals("Accepted", newRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", newRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Approved", newRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList.get(2).get("status").trim());
        assertEquals("1st re-issue of original refund " + refundReference, newRefundStatusHistoryList.get(2).get("notes").trim());
    }

    @Test
    public void positive_reissue_refund_for_postal_order_payment() throws Exception {
        String emailAddress = dataGenerator.generateEmail(16);
        final String service = "Probate";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "300.00";
        final String paymentMethod = "postalorder";
        final Integer bankGiroSlipNo = 965556;

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        String[] dcn = {"6600000000001" + RandomUtils.nextInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER)};

        BulkScanDcnPayment bulkScanDcnPayment = createBulkScanDcnPayment(
            new BigDecimal(feeAmount),
            bankGiroSlipNo,
            LocalDate.now().toString(),
            "GBP",
            dcn[0],
            paymentMethod
        );
        Response bulkScanDcnPaymentResponse = paymentTestService.postBulkScanDcnPayment(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanDcnPayment
        );
        bulkScanDcnPaymentResponse.then().statusCode(CREATED.value()).and().toString().equals("created");

        BulkScanCcdPayment bulkScanCcdPayment = createBulkScanCcdPayments(ccdCaseNumber,
                                                                          dcn,
                                                                          "AA08",
                                                                          false);
        Response bulkScanCcdPaymentsResponse = paymentTestService.postBulkScanCcdPayments(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.bulkscanApiUrl,
            bulkScanCcdPayment
        );
        bulkScanCcdPaymentsResponse.then().statusCode(CREATED.value()).body(
            "payment_dcns",
            equalTo(Arrays.asList(dcn))
        );

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(feeAmount))
            .service(service)
            .siteId("AA08")
            .currency("GBP")
            .documentControlNumber(dcn[0])
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User1")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.POSTAL_ORDER)
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentProvider("exela")
            .giroSlipNo(bankGiroSlipNo.toString())
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                                    .calculatedAmount(new BigDecimal(feeAmount))
                                    .code(feeCode)
                                    .version(feeVersion)
                                    .reference("testRef1")
                                    .volume(1)
                                    .ccdCaseNumber(ccdCaseNumber)
                                    .build())).build();

        Response paymentGroupResponse = paymentTestService.addNewFeeAndPaymentGroup(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                    SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                    testConfigProperties.basePaymentsUrl,
                                                                                    paymentGroupDto
        );

        assertThat(paymentGroupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        String paymentGroupReference = paymentGroupResponse.getBody().jsonPath().getString("payment_group_reference");

        Response paymentResponse = paymentTestService.createBulkScanPayment(
            USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            testConfigProperties.basePaymentsUrl,
            bulkScanPaymentRequest,
            paymentGroupReference
        );

        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String paymentReference = paymentResponse.getBody().jsonPath().getString("reference");

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "20",
                                                                              testConfigProperties.basePaymentsUrl
        );

        final String refundReference = performRefund(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, emailAddress);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        // verify that status changed to Approved
        RefundDto refundDto1 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.APPROVED, refundDto1.getRefundStatus());
        assertEquals("Amended claim", refundDto1.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto2 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto2.getRefundStatus());
        assertEquals("Amended claim", refundDto2.getReason());

        // Verify notification email content for Refund Accepted after Rejected
        Notification notification1 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        String emailBody1 = notifyUtil.getNotifyEmailBody(notification1);
        assertTrue(emailBody1.contains("Refund reference: " + refundReference),
                   "Email body does not contain the refund reference");
        assertTrue(emailBody1.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Expired
        RefundDto refundDto3 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.EXPIRED, refundDto3.getRefundStatus());
        assertEquals("Amended claim", refundDto3.getReason());

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String newRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(newRefundReference).matches()).isEqualTo(true);
        refundReferences.add(newRefundReference);

        // verify that old refund status changed to Closed
        RefundDto refundDto4 = getRefundListByCaseNumber(ccdCaseNumber, refundReference);
        assertEquals(RefundStatus.CLOSED, refundDto4.getRefundStatus());
        assertEquals("Amended claim", refundDto4.getReason());

        // verify that new refund status changed to Approved immediately after Ressiued and Reason remain same
        // Reissued status will be verified in status history as the refund list response returns the latest status only
        RefundDto refundDto5 = getRefundListByCaseNumberAndStatus(ccdCaseNumber, newRefundReference, "Approved");
        assertEquals(RefundStatus.APPROVED, refundDto5.getRefundStatus());
        assertEquals("Amended claim", refundDto5.getReason());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            newRefundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that status changed to Accepted
        RefundDto refundDto6 = getRefundListByCaseNumber(ccdCaseNumber, newRefundReference);
        assertEquals(RefundStatus.ACCEPTED, refundDto6.getRefundStatus());
        assertEquals("Amended claim", refundDto6.getReason());

        // Verify notification email content for Refund Accepted after Reissued
        Notification notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
        if (notification2.getId().equals(notification1.getId())) {
            // try again
            try {
                Thread.sleep(2000); // wait for 2 seconds before retrying
                LOG.info("Retrying to fetch notification email for {}", emailAddress);
                notification2 = notifyUtil.findEmailNotificationFromNotifyClient(emailAddress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotEquals(notification2.getId(), notification2.getId(), "New notification not sent"); //ensure it's a new notification

        String emailBody2 = notifyUtil.getNotifyEmailBody(notification2);
        assertTrue(emailBody2.contains("Refund reference: " + newRefundReference),
                   "Email body does not contain new refund reference");
        assertTrue(emailBody2.contains("To receive this refund, you must give us the correct bank details to process the request."),
                   "Email body does not contain expected text for Refund Accepted");

        //verify the old refund status history
        Response oldRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(oldRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> oldRefundStatusHistoryList =
            oldRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(8, oldRefundStatusHistoryList.size());

        assertEquals("Closed", oldRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Refund closed by case worker", oldRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Expired", oldRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Unable to process expired refund", oldRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Accepted", oldRefundStatusHistoryList.get(5).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", oldRefundStatusHistoryList.get(5).get("notes").trim());

        assertEquals("Approved", oldRefundStatusHistoryList.get(6).get("status").trim());
        assertEquals("Sent to middle office", oldRefundStatusHistoryList.get(6).get("notes").trim());

        assertEquals("Sent for approval", oldRefundStatusHistoryList.get(7).get("status").trim());
        assertEquals("Refund initiated and sent to team leader", oldRefundStatusHistoryList.get(7).get("notes").trim());

        //verify the new refund status history
        Response newRefundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, newRefundReference
            );
        assertThat(newRefundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> newRefundStatusHistoryList =
            newRefundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        assertEquals(3, newRefundStatusHistoryList.size());

        assertEquals("Accepted", newRefundStatusHistoryList.get(0).get("status").trim());
        assertEquals("Sent to Middle Office for Processing", newRefundStatusHistoryList.get(0).get("notes").trim());

        assertEquals("Approved", newRefundStatusHistoryList.get(1).get("status").trim());
        assertEquals("Refund approved by system", newRefundStatusHistoryList.get(1).get("notes").trim());

        assertEquals("Reissued", newRefundStatusHistoryList.get(2).get("status").trim());
        assertEquals("1st re-issue of original refund " + refundReference, newRefundStatusHistoryList.get(2).get("notes").trim());
    }

    private String createPayment(String service, String siteId, String ccdCaseNumber, String feeAmount, String feeCode, String feeVersion) {
        final String accountNumber = testConfigProperties.existingAccountNumber;

        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequest(
                service,
                siteId,
                accountNumber,
                ccdCaseNumber,
                feeAmount,
                feeCode,
                feeVersion
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        PaymentDto paymentDto = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Get pba payment by reference
        PaymentDto paymentsResponse =
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal(feeAmount));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        return paymentsResponse.getReference();
    }

    @NotNull
    private String performRefund(String refundReason, String paymentReference,
                                 String refundAmount, String feeAmount,
                                 String feeCode, String feeVersion) {
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
        return refundReference;
    }

    @NotNull
    private String performRefund(String refundReason, String paymentReference,
                                 String refundAmount, String feeAmount,
                                 String feeCode, String feeVersion, String emailAddress) {
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest(refundReason, paymentReference, refundAmount, feeAmount, feeCode, feeVersion, feeId, emailAddress);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
        return refundReference;
    }

    private RefundDto getRefundListByCaseNumber(String ccdCaseNumber, String refundReference) {
        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       ccdCaseNumber
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);

        return refundsListDto.getRefundList().stream()
            .filter(rf -> rf.getRefundReference().equals(refundReference)).findFirst().get();
    }

    private RefundDto getRefundListByCaseNumberAndStatus(String ccdCaseNumber, String refundReference, String status) {
        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       ccdCaseNumber, status, "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);

        return refundsListDto.getRefundList().stream()
            .filter(rf -> rf.getRefundReference().equals(refundReference)).findFirst().get();
    }

    public static BulkScanDcnPayment createBulkScanDcnPayment(BigDecimal amount, Integer bankGiroCreditSlipNumber, String bankedDate,
                                                           String currency, String dcnReference, String method) {

        return BulkScanDcnPayment
            .createPaymentRequestWith()
            .amount(amount)
            .bankGiroCreditSlipNumber(bankGiroCreditSlipNumber)
            .bankedDate(bankedDate)
            .currency(currency)
            .dcnReference(dcnReference)
            .method(method)
            .build();
    }

    public static BulkScanCcdPayment createBulkScanCcdPayments(String ccdCaseNumber, String[] dcn,
                                                                   String responsibleServiceId, boolean isExceptionRecord) {
        return BulkScanCcdPayment
            .createBSPaymentRequestWith()
            .ccdCaseNumber(ccdCaseNumber)
            .documentControlNumbers(dcn)
            .responsibleServiceId(responsibleServiceId)
            .isExceptionRecord(isExceptionRecord)
            .build();
    }

    @After
    public void deleteRefundsAndPayments() {
        /*if (!refundReferences.isEmpty()) {
            //delete refund record
            refundReferences.forEach(refundReference -> paymentTestService.deleteRefund(
                USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                refundReference
            ).then().statusCode(NO_CONTENT.value()));
        }
        if (!paymentReferences.isEmpty()) {
            // delete payment record
            paymentReferences.forEach(paymentReference -> paymentTestService.deletePayment(
                USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                paymentReference,
                testConfigProperties.basePaymentsUrl
            ).then().statusCode(NO_CONTENT.value()));
        }*/
    }

    @AfterClass
    public static void tearDown() {
        /* if (!userEmails.isEmpty()) {
            // delete idam test user
            userEmails.forEach(IdamService::deleteUser);
        } */
    }

}
