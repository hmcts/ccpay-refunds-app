package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.NotNull;
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
import uk.gov.hmcts.reform.refunds.dtos.RefundsReportDto;
import uk.gov.hmcts.reform.refunds.dtos.RefundsReportResponse;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.functional.config.CcdService;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.functional.config.ValidUser;
import uk.gov.hmcts.reform.refunds.functional.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentDto;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.functional.service.PaymentTestService;
import uk.gov.hmcts.reform.refunds.functional.util.DataGenerator;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@ActiveProfiles("functional")
@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest
public class RefundsReportFunctionalTest {

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
    private DataGenerator dataGenerator;

    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
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
    public void positive_refund_report_team_leader_approved_refund() {
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

        //Refund Approved by Team leader
        final String refundReference = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
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

        String dateFrom = getReportDate(new Date(System.currentTimeMillis()));
        String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        Response refundsReportData = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                   SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse =
            refundsReportData.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto = refundsReportResponse.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto).isNotNull();
        assertThat(getReportDate(refundReportDto.getRefundDateCreated())).isEqualTo(dateFrom);
        assertThat(getReportDate(refundReportDto.getRefundDateUpdated())).isEqualTo(dateFrom);
        assertThat(refundReportDto.getAmount()).isEqualTo(new BigDecimal(refundAmount));
        assertThat(refundReportDto.getRefundReference()).isEqualTo(refundReference);
        assertThat(refundReportDto.getPaymentReference()).isEqualTo(paymentReference);
        assertThat(refundReportDto.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
        assertThat(refundReportDto.getServiceType()).isEqualToIgnoringCase(service);
        assertThat(refundReportDto.getRefundStatus()).isEqualTo("Approved");
        assertThat(refundReportDto.getNotes()).isEqualTo("Sent to middle office");
    }

    @Test
    public void positive_refund_report_Liberata_updated_refunds() {
        String emailAddress = dataGenerator.generateEmail(16);
        final String service = "PROBATE";
        final String siteId = "ABA6";
        final String feeAmount = "300.00";
        final String feeCode = "FEE0219";
        final String feeVersion = "6";
        final String refundReason = "RR001";
        final String refundAmount = "10.00";

        String ccdCaseNumber = ccdService.createProbateDraftCase(
            USER_ID_PROBATE_DRAFT_CASE_CREATOR,
            USER_TOKEN_PROBATE_DRAFT_CASE_CREATOR,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT
        );
        LOG.info("Probate draft case number : {}", ccdCaseNumber);

        final String paymentReference = createPayment(service, siteId, ccdCaseNumber, feeAmount, feeCode, feeVersion);
        paymentReferences.add(paymentReference);

        final String dateFrom = getReportDate(new Date(System.currentTimeMillis()));
        final String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        // Refund Accepted by Liberata
        final String refundReference1 = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference1);

        Response responseReviewRefund1 = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference1,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund1.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund1.getBody().asString()).isEqualTo("Refund approved");

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference1,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Refund Rejected by Liberata with the reason `Transaction not yet received in API`
        final String refundReference2 = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference2);

        Response responseReviewRefund2 = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference2,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund2.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund2.getBody().asString()).isEqualTo("Refund approved");

        // Liberata Rejected the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference2,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Transaction not yet received in API")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Refund Rejected by Liberata with the reason `Case details do not match`
        final String refundReference3 = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference3);

        Response responseReviewRefund3 = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference3,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund3.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund3.getBody().asString()).isEqualTo("Refund approved");

        // Liberata Rejected the Refund
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference3,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Case details do not match")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Refund Rejected by Liberata with the reason `Insufficient funds`
        final String refundReference4 = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference4);

        Response responseReviewRefund4 = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference4,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund4.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund4.getBody().asString()).isEqualTo("Refund approved");

        // Liberata Rejected the Refund
        Response updateRefundStatusResponse4 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference4,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Insufficient funds")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse4.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Refund Rejected by Liberata with the reason `Settlement not received `
        final String refundReference5 = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference5);

        Response responseReviewRefund5 = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference5,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund5.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund5.getBody().asString()).isEqualTo("Refund approved");

        // Liberata Rejected the Refund
        Response updateRefundStatusResponse5 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference5,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Settlement not received ")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse5.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        Response refundsReportData = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                    SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);
        assertThat(refundsReportData.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse =
            refundsReportData.getBody().as(RefundsReportResponse.class);

        refundReferences.forEach(refundReference -> {
            RefundsReportDto refundReportDto = refundsReportResponse.getRefundsReportList().stream()
                .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
                .findFirst()
                .orElse(null);
            assertThat(refundReportDto).isNotNull();
            assertThat(getReportDate(refundReportDto.getRefundDateCreated())).isEqualTo(dateFrom);
            assertThat(getReportDate(refundReportDto.getRefundDateUpdated())).isEqualTo(dateFrom);
            assertThat(refundReportDto.getAmount()).isEqualTo(new BigDecimal(refundAmount));
            assertThat(refundReportDto.getRefundReference()).isEqualTo(refundReference);
            assertThat(refundReportDto.getPaymentReference()).isEqualTo(paymentReference);
            assertThat(refundReportDto.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
            assertThat(refundReportDto.getServiceType()).isEqualToIgnoringCase(service);
        });
    }

    @Test
    public void positive_refund_report_expired_refund() {
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

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Verify the Expired refund in the report
        String dateFrom = getReportDate(new Date(System.currentTimeMillis()));
        String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        Response refundsReportData1 = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                    SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData1.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse1 =
            refundsReportData1.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto1 = refundsReportResponse1.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto1).isNotNull();
        assertThat(getReportDate(refundReportDto1.getRefundDateCreated())).isEqualTo(dateFrom);
        assertThat(getReportDate(refundReportDto1.getRefundDateUpdated())).isEqualTo(dateFrom);
        assertThat(refundReportDto1.getAmount()).isEqualTo(new BigDecimal(refundAmount));
        assertThat(refundReportDto1.getRefundReference()).isEqualTo(refundReference);
        assertThat(refundReportDto1.getPaymentReference()).isEqualTo(paymentReference);
        assertThat(refundReportDto1.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
        assertThat(refundReportDto1.getServiceType()).isEqualToIgnoringCase(service);
        assertThat(refundReportDto1.getRefundStatus()).isEqualTo("Expired");
        assertThat(refundReportDto1.getNotes()).isEqualTo("Unable to process expired refund");
    }

    @Test
    public void positive_refund_report_closed_and_system_approved_refunds() {
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

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse1 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        //Liberata rejected the refund with the reason 'Unable to apply refund to Card'
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Liberata Accepted the Refund
        Response updateRefundStatusResponse2 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith()
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build()
        );
        assertThat(updateRefundStatusResponse2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Liberata expired the refund after 21 days
        Response updateRefundStatusResponse3 = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason("Unable to process expired refund")
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED).build()
        );
        assertThat(updateRefundStatusResponse3.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // Verify the Expired refund in the report
        String dateFrom = getReportDate(new Date(System.currentTimeMillis()));
        String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        Response refundsReportData1 = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                     SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData1.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse1 =
            refundsReportData1.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto1 = refundsReportResponse1.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto1).isNotNull();
        assertThat(getReportDate(refundReportDto1.getRefundDateCreated())).isEqualTo(dateFrom);
        assertThat(getReportDate(refundReportDto1.getRefundDateUpdated())).isEqualTo(dateFrom);
        assertThat(refundReportDto1.getAmount()).isEqualTo(new BigDecimal(refundAmount));
        assertThat(refundReportDto1.getRefundReference()).isEqualTo(refundReference);
        assertThat(refundReportDto1.getPaymentReference()).isEqualTo(paymentReference);
        assertThat(refundReportDto1.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
        assertThat(refundReportDto1.getServiceType()).isEqualToIgnoringCase(service);
        assertThat(refundReportDto1.getRefundStatus()).isEqualTo("Expired");
        assertThat(refundReportDto1.getNotes()).isEqualTo("Unable to process expired refund");

        // Reissue the expired refund
        Response reissueExpiredRefundResponse = paymentTestService.reissueExpiredRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
        assertThat(reissueExpiredRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        String reIssuedRefundReference = reissueExpiredRefundResponse.getBody().jsonPath().getString("refund_reference");
        assertThat(REFUNDS_REGEX_PATTERN.matcher(reIssuedRefundReference).matches()).isEqualTo(true);
        refundReferences.add(reIssuedRefundReference);

        // verify the Closed refund in the report
        Response refundsReportData2 = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                     SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData2.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse2 =
            refundsReportData2.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto2 = refundsReportResponse2.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto2).isNotNull();
        assertThat(getReportDate(refundReportDto2.getRefundDateCreated())).isEqualTo(dateFrom);
        assertThat(getReportDate(refundReportDto2.getRefundDateUpdated())).isEqualTo(dateFrom);
        assertThat(refundReportDto2.getAmount()).isEqualTo(new BigDecimal(refundAmount));
        assertThat(refundReportDto2.getRefundReference()).isEqualTo(refundReference);
        assertThat(refundReportDto2.getPaymentReference()).isEqualTo(paymentReference);
        assertThat(refundReportDto2.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
        assertThat(refundReportDto2.getServiceType()).isEqualToIgnoringCase(service);
        assertThat(refundReportDto2.getRefundStatus()).isEqualTo("Closed");
        assertThat(refundReportDto2.getNotes()).isEqualTo("Refund closed by case worker");

        // verify that new refund is approved by system user in the report

        Response refundsReportData3 = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                     SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData3.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse3 =
            refundsReportData3.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto3 = refundsReportResponse3.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(reIssuedRefundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto3).isNotNull();
        assertThat(getReportDate(refundReportDto3.getRefundDateCreated())).isEqualTo(dateFrom);
        assertThat(getReportDate(refundReportDto3.getRefundDateUpdated())).isEqualTo(dateFrom);
        assertThat(refundReportDto3.getAmount()).isEqualTo(new BigDecimal(refundAmount));
        assertThat(refundReportDto3.getRefundReference()).isEqualTo(reIssuedRefundReference);
        assertThat(refundReportDto3.getPaymentReference()).isEqualTo(paymentReference);
        assertThat(refundReportDto3.getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
        assertThat(refundReportDto3.getServiceType()).isEqualToIgnoringCase(service);
        assertThat(refundReportDto3.getRefundStatus()).isEqualTo("Approved");
        assertThat(refundReportDto3.getNotes()).isEqualTo("Refund approved by system");
    }

    @Test
    public void negative_refund_report_not_have_team_leader_rejected_refunds() {
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
        final String refundReference = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
        refundReferences.add(refundReference);

        // Reject the refund
        Response responseReviewRefund
            = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.REJECT.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE003")
                .reason("The case details don’t match the help with fees details").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund rejected");

        String dateFrom = getReportDate(new Date(System.currentTimeMillis()));
        String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        Response refundsReportData = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                   SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);

        assertThat(refundsReportData.getStatusCode()).isEqualTo(OK.value());
        RefundsReportResponse refundsReportResponse =
            refundsReportData.getBody().as(RefundsReportResponse.class);
        RefundsReportDto refundReportDto = refundsReportResponse.getRefundsReportList().stream()
            .filter(reportDto -> reportDto.getRefundReference().equals(refundReference))
            .findFirst()
            .orElse(null);
        assertThat(refundReportDto).isNull();
    }

    @Test
    public void negative_refund_report_from_date_greater_than_to_date() {
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
        final String refundReference = performRefund(
            refundReason,
            paymentReference,
            refundAmount,
            feeAmount,
            feeCode,
            feeVersion,
            emailAddress
        );
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

        String dateFrom = getReportDate(DateUtils.addDays(new Date(System.currentTimeMillis()), 1));
        String dateTo = getReportDate(new Date(System.currentTimeMillis()));

        Response refundsReportData = paymentTestService.getRefundsByStartAndEndDate(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                   SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, dateFrom, dateTo);
        assertThat(refundsReportData.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertThat(refundsReportData.getBody().asString()).isEqualTo("Start date cannot be greater than end date");
    }

    public String getReportDate(Date date) {
        java.time.format.DateTimeFormatter reportNameDateFormat = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
        return date == null ? null : java.time.LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(reportNameDateFormat);
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

    @After
    public void deletePayment() {
        if (!refundReferences.isEmpty()) {
            //delete refund record
            refundReferences.forEach(refundReference -> paymentTestService.deleteRefund(
                USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                refundReference
            ));
        }
        if (!paymentReferences.isEmpty()) {
            // delete payment record
            paymentReferences.forEach(paymentReference -> paymentTestService.deletePayment(
                USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                paymentReference,
                testConfigProperties.basePaymentsUrl
            ).then().statusCode(NO_CONTENT.value()));
        }
    }

    @AfterClass
    public static void tearDown() {
        if (!userEmails.isEmpty()) {
            // delete idam test user
            userEmails.forEach(IdamService::deleteUser);
        }
    }
}
