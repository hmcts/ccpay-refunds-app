package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.config.IdamService;
import uk.gov.hmcts.reform.refunds.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.response.PaymentDto;
import uk.gov.hmcts.reform.refunds.response.PaymentsResponse;
import uk.gov.hmcts.reform.refunds.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.service.PaymentTestService;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

import static io.restassured.RestAssured.expect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@ActiveProfiles({"functional", "liberataMock"})
@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest
public class RefundsApproverJourneyFunctionalTest {

    @Autowired
    private TestConfigProperties testConfigProperties;

    @Autowired
    private IdamService idamService;

    @Autowired
    private S2sTokenService s2sTokenService;

    @Inject
    private PaymentTestService paymentTestService;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static String USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE;
    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_CITIZEN_WITH_PAYMENTS_ROLE;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
    private static String SERVICE_TOKEN_CMC;
    private static boolean TOKENS_INITIALIZED;
    private static final Pattern
        REFUNDS_REGEX_PATTERN = Pattern.compile("^(RF)-([0-9]{4})-([0-9-]{4})-([0-9-]{4})-([0-9-]{4})$");

    @Before
    public void setUp() throws Exception {

        RestAssured.baseURI = testConfigProperties.baseTestUrl;

        if (!TOKENS_INITIALIZED) {
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE =
                idamService.createUserWith(IdamService.CMC_CASE_WORKER_GROUP, "caseworker-cmc-solicitor")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE =
                idamService.createUserWith(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver")
                    .getAuthorisationToken();

            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);


            USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE =
                idamService.createUserWith(IdamService.CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void testGetReasons() {

        expect().given()
            .relaxedHTTPSValidation()
            .header("Authorization", USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE)
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(APPLICATION_JSON_VALUE)
            .accept(APPLICATION_JSON_VALUE)
            .when()
            .get("/refund/reasons")
            .then()
            .statusCode(200);
        //assertFalse(testUrl.isEmpty(), "The test has completed...");
    }

    @Test
    public void test_reject_a_refund_request() {

        final String refundReference = performRefund();

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);

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
    }

    @Test
    public void test_sendback_a_refund_request() {

        final String refundReference = performRefund();

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
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund returned to caseworker");
    }

    @Test
    @Ignore("HTTP Status of 500 as Application Integrating with Liberata")
    public void test_approve_a_refund_request() {

        final String refundReference = performRefund();

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
            //RefundReviewRequest.buildRefundReviewRequest().code("RE004")
            // .reason("More evidence is required").build());
            RefundReviewRequest.buildRefundReviewRequest().build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");
    }

    @Test
    //@Ignore
    public void test_negative_unknown_action_refund_request() {

        final String refundReference = performRefund();

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
            "NO DEFINED STATUS",
            RefundReviewRequest.buildRefundReviewRequest()
                .code("RE003")
                .reason("The case details don’t match the help with fees details")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(BAD_REQUEST.value());
    }

    @Test
    public void test_negative_unauthorized_user_refund_request() {

        final String refundReference = performRefund();

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
            USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            "NO DEFINED STATUS",
            RefundReviewRequest.buildRefundReviewRequest()
                .code("RE003")
                .reason("The case details don’t match the help with fees details")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(FORBIDDEN.value());
    }

    @Test
    //@Ignore("As Refund List is returning more than one Refund in its Get List....")
    public void test_resubmit_refund_journey() {

        final String refundReference = performRefund();
        /*final String accountNumber = testConfigProperties.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService
            .getPbaPaymentsByAccountNumber(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                accountNumber,
                testConfigProperties.basePaymentsUrl
            )
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        Optional<PaymentDto> paymentDtoOptional
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> {
                return s2.getDateCreated().compareTo(s1.getDateCreated());
            }).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        System.out.println("The value of the CCD Case Number " + paymentDtoOptional.get().getCcdCaseNumber());
        String paymentReference = paymentDtoOptional.get().getPaymentReference();
        System.out.println("The value of the Payment Reference : " + paymentDtoOptional.get().getCcdCaseNumber());

        PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        System.out.println(refundResponse.getStatusLine());
        System.out.println(refundResponse.getBody().prettyPrint());
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
*/
        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            "SENDBACK",
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       "sent back", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream().sorted((s1, s2) -> {
            return s2.getDateCreated().compareTo(s1.getDateCreated());
        }).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval") || entry.get("status").trim().equals("sentback"))
                .isTrue();
        });
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            ResubmitRefundRequest.ResubmitRefundRequestWith()
                .amount(new BigDecimal("80.00"))
                .refundReason("RR004").build(),
            refundReferenceFromRefundList
        );
        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    @Ignore("Need Support to complete this test....")
    public void test_approval_journey() {

        final String refundReference = performRefund();
        /*final String accountNumber = testConfigProperties.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService
            .getPbaPaymentsByAccountNumber(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                accountNumber,
                testConfigProperties.basePaymentsUrl
            )
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        Optional<PaymentDto> paymentDtoOptional
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> {
                return s2.getDateCreated().compareTo(s1.getDateCreated());
            }).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        System.out.println("The value of the CCD Case Number " + paymentDtoOptional.get().getCcdCaseNumber());
        String paymentReference = paymentDtoOptional.get().getPaymentReference();
        System.out.println("The value of the Payment Reference : " + paymentDtoOptional.get().getCcdCaseNumber());

        PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        System.out.println(refundResponse.getStatusLine());
        System.out.println(refundResponse.getBody().prettyPrint());
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
*/
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundEvent.APPROVE.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       "sent for approval", "true"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundsListDto.getRefundList().size()).isEqualTo(1);
        String refundReferenceFromTheRefundList = refundsListDto.getRefundList().get(0).getRefundReference();

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval"))
                .isTrue();
        });

        //Further call out to Liberata to approve from Liberata's side.
        paymentTestService.updateRefundStatus(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                              SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                              refundReference,
                                              RefundStatusUpdateRequest.RefundRequestWith().status(RefundStatus.ACCEPTED)
                                                  .reason("This is a valid Request").build());
        Response refundStatusHistoryResponsePostApproval =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryResponsePostApproval.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryListPostApproval =
            refundStatusHistoryResponsePostApproval.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval") || entry.get("status").trim().equals("approved"))
                .isTrue();
        });
    }

    @Test
    @Ignore
    public void test_rejected_journey() {

        final String refundReference = performRefund();
        /*final String accountNumber = testConfigProperties.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService
            .getPbaPaymentsByAccountNumber(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                accountNumber,
                testConfigProperties.basePaymentsUrl
            )
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        Optional<PaymentDto> paymentDtoOptional
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> {
                return s2.getDateCreated().compareTo(s1.getDateCreated());
            }).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        System.out.println("The value of the CCD Case Number " + paymentDtoOptional.get().getCcdCaseNumber());
        String paymentReference = paymentDtoOptional.get().getPaymentReference();
        System.out.println("The value of the Payment Reference : " + paymentDtoOptional.get().getCcdCaseNumber());

        PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        System.out.println(refundResponse.getStatusLine());
        System.out.println(refundResponse.getBody().prettyPrint());
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
*/
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundEvent.APPROVE.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       "sent for approval", "true"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundsListDto.getRefundList().size()).isEqualTo(1);
        String refundReferenceFromTheRefundList = refundsListDto.getRefundList().get(0).getRefundReference();

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval"))
                .isTrue();
        });

        //Further call out to Liberata to approve from Liberata's side.
        paymentTestService.updateRefundStatus(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                              SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                              refundReference,
                                              RefundStatusUpdateRequest.RefundRequestWith().status(RefundStatus.REJECTED)
                                                  .reason("This is an invalid Request").build());
        Response refundStatusHistoryResponsePostApproval =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryResponsePostApproval.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryListPostApproval =
            refundStatusHistoryResponsePostApproval.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval") || entry.get("status").trim().equals("rejected"))
                .isTrue();
        });
    }

    @Test
    @Ignore
    public void test_invalid_liberata_update_journey() {

        final String refundReference = performRefund();
        /*final String accountNumber = testConfigProperties.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService
            .getPbaPaymentsByAccountNumber(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                accountNumber,
                testConfigProperties.basePaymentsUrl
            )
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        Optional<PaymentDto> paymentDtoOptional
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> {
                return s2.getDateCreated().compareTo(s1.getDateCreated());
            }).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        System.out.println("The value of the CCD Case Number " + paymentDtoOptional.get().getCcdCaseNumber());
        String paymentReference = paymentDtoOptional.get().getPaymentReference();
        System.out.println("The value of the Payment Reference : " + paymentDtoOptional.get().getCcdCaseNumber());

        PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        System.out.println(refundResponse.getStatusLine());
        System.out.println(refundResponse.getBody().prettyPrint());
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
*/
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundEvent.APPROVE.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       "sent for approval", "true"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundsListDto.getRefundList().size()).isEqualTo(1);
        String refundReferenceFromTheRefundList = refundsListDto.getRefundList().get(0).getRefundReference();

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval"))
                .isTrue();
        });

        //Further call out to Liberata to approve from Liberata's side.
        paymentTestService.updateRefundStatus(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                              SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                              refundReference,
                                              RefundStatusUpdateRequest.RefundRequestWith().status(RefundStatus.REJECTED)
                                                  .reason("This is an invalid Request").build());
        Response refundStatusHistoryResponsePostApproval =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromTheRefundList
            );
        assertThat(refundStatusHistoryResponsePostApproval.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryListPostApproval =
            refundStatusHistoryResponsePostApproval.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.stream().forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("sent for approval") || entry.get("status").trim().equals("rejected"))
                .isTrue();
        });
    }


    @NotNull
    private String performRefund() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        final PaymentsResponse paymentsResponse = paymentTestService
            .getPbaPaymentsByAccountNumber(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                accountNumber,
                testConfigProperties.basePaymentsUrl
            )
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        final Optional<PaymentDto> paymentDtoOptional
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> {
                return s2.getDateCreated().compareTo(s1.getDateCreated());
            }).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        System.out.println("The value of the CCD Case Number " + paymentDtoOptional.get().getCcdCaseNumber());
        final String paymentReference = paymentDtoOptional.get().getPaymentReference();
        System.out.println("The value of the Payment Reference : " + paymentDtoOptional.get().getCcdCaseNumber());

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        System.out.println(refundResponse.getStatusLine());
        System.out.println(refundResponse.getBody().prettyPrint());
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);
        return refundReference;
    }

}
