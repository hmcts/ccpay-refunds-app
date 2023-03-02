package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.FromTemplateContact;
import uk.gov.hmcts.reform.refunds.dtos.requests.MailAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RerfundLiberataResponse;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.functional.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.response.PaymentDto;
import uk.gov.hmcts.reform.refunds.functional.response.PaymentsResponse;
import uk.gov.hmcts.reform.refunds.functional.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.functional.service.PaymentTestService;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@ActiveProfiles("functional")
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
    @Autowired
    private RefundsRepository refundsRepository;

    private static String USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE;
    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE;
    private static String SERVICE_TOKEN_CMC;
    private static boolean TOKENS_INITIALIZED;
    private static String USER_TOKEN_CMC_CASE_WORKER_WITH_PAYMENT_ROLE;
    private static final Pattern
        REFUNDS_REGEX_PATTERN = Pattern.compile("^(RF)-([0-9]{4})-([0-9-]{4})-([0-9-]{4})-([0-9-]{4})$");

    @Before
    public void setUp() {

        RestAssured.baseURI = testConfigProperties.baseTestUrl;
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE =
                idamService.createUserWith(IdamService.CMC_CASE_WORKER_GROUP, "caseworker-cmc-solicitor")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund",
                                                      "payments-refund-divorce", "payments-refund-probate")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver",
                                                      "payments-refund-approver-divorce", "payments-refund-approver-probate")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver",
                                                      "payments-refund-probate", "payments-refund-approver-probate")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver",
                                                      "payments-refund-approver-divorce", "payments-refund-approver-probate", "payments")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE_WITHOUT_SERVICE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver", "payments")
                    .getAuthorisationToken();

            System.out.println("testConfigProperties.cmcS2SSecret ---> " + testConfigProperties.cmcS2SSecret);
            System.out.println("testConfigProperties.cmcS2SName ---> " + testConfigProperties.cmcS2SName);
            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);

            USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE =
                idamService.createUserWith(IdamService.CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);

            TOKENS_INITIALIZED = true;

            USER_TOKEN_CMC_CASE_WORKER_WITH_PAYMENT_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP,"payments")
                    .getAuthorisationToken();

        }
    }

    @Test
    public void positive_get_reasons() {

        Response responseRefundReasons
            = paymentTestService
            .getRefundReasons(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT);
        assertThat(responseRefundReasons.getStatusCode()).isEqualTo(OK.value());
        RefundReason overPay = new RefundReason();
        overPay.setCode("RR037");
        overPay.setName("Overpayment");
        overPay.setDescription("Overpayment");
        List<RefundReason> refundReasons = responseRefundReasons.getBody().jsonPath().get("$");
        assertThat(refundReasons.size()).isEqualTo(35);
        assertThat(refundReasons.contains(overPay));

    }


    @Test
    public void positive_reject_a_refund_request() {
        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

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
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Rejected")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("The case details don’t match the help with fees details")
                    || entry.get("notes").trim().equals("Refund initiated and sent to team leader"))
                .isTrue();
        });

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void nagative_create_refund_without_service_role() {
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "DIVORCE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        String paymentReference =  paymentsResponse.getReference();

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", paymentId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
    }

    @Test
    public void positive_sendback_a_refund_request() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_sendback_a_refund_request_without_service_role() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CONFLICT.value());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_get_refund_with_ccdcasenumber_for_an_approver_without_probate_service() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();
        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90.00", "550", paymentId);
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

        // Create Payment 2
        final CreditAccountPaymentRequest accountPaymentRequest1 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "100.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest1.setAccountNumber(accountNumber);
        PaymentDto paymentDto1 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest1
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(),"90", "550", paymentId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference1).matches()).isEqualTo(true);

        // Create Payment 3
        final CreditAccountPaymentRequest accountPaymentRequest2 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "190.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest2.setAccountNumber(accountNumber);
        PaymentDto paymentDto2 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest2
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5", testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(),"90", "550", paymentId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference2).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertEquals(refundListDtoResponse.getRefundList().size(), 3);
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto1.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto2.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());

        // delete refunds records
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference1);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference2);
    }

    @Test
    public void positive_get_refund_only_for_probate_service_role() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90.00", "550", paymentId);
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

        // Create Payment 2
        final CreditAccountPaymentRequest accountPaymentRequest1 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "100.00",
                "DIVORCE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest1.setAccountNumber(accountNumber);
        PaymentDto paymentDto1 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest1
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(),"90", "550", paymentId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference1).matches()).isEqualTo(true);

        // Create Payment 3
        final CreditAccountPaymentRequest accountPaymentRequest2 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "190.00",
                "DIVORCE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest2.setAccountNumber(accountNumber);
        PaymentDto paymentDto2 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest2
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5", testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(),"90", "550", paymentId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference2).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE,
                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                       "Sent for approval", "false");

        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertEquals(refundListDtoResponse.getRefundList().size(), 1);
        assertEquals(refundListDtoResponse.getRefundList().get(0).getServiceType(), "Probate");
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto1.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto2.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());

        // delete refunds records
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference1);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference2);
    }

    @Test
    public void positive_get_refund_list_for_an_approver() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90.00", "550", paymentId);
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

        // Create Payment 2
        final CreditAccountPaymentRequest accountPaymentRequest1 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "100.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest1.setAccountNumber(accountNumber);
        PaymentDto paymentDto1 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest1
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(),"90", "550", paymentId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference1).matches()).isEqualTo(true);

        // Create Payment 3
        final CreditAccountPaymentRequest accountPaymentRequest2 = RefundsFixture
            .pbaPaymentRequestForProbate(
                "190.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest2.setAccountNumber(accountNumber);
        PaymentDto paymentDto2 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest2
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5", testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(),"90", "550", paymentId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference2).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(1).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(2).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getDescription())
            .isEqualTo("Refund request submitted");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto1.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto2.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());

        // delete refunds records
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference1);
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference2);
    }

    @Test
    public void negative_approver_can_request_refund_but_not_self_approve() {

        final String refundReference = performRefundByApprover();
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(FORBIDDEN.value());

        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_approve_a_refund_request() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Approved")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Sent to middle office")
                    || entry.get("notes").trim().equals("Refund initiated and sent to team leader"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        //Should be verified as a call out to Liberata....

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void nagative_approve_a_refund_request_without_service_role() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CONFLICT.value());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_approve_a_refund_request_with_template_preview_for_email() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

        final RefundReviewRequest  refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
            .code("RE001")
            .reason("Wrong Data")
            .templatePreview(TemplatePreview.templatePreviewWith()
                                 .id(UUID.randomUUID())
                                 .templateType("email")
                                 .version(11)
                                 .body("Dear Sir Madam")
                                 .subject("HMCTS refund request approved")
                                 .html("Dear Sir Madam")
                                 .from(FromTemplateContact
                                           .buildFromTemplateContactWith()
                                           .fromEmailAddress("test@test.com")
                                           .build())
                                 .build())
            .build();

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            refundReviewRequest
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Approved")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Sent to middle office")
                    || entry.get("notes").trim().equals("Refund initiated and sent to team leader"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        //Should be verified as a call out to Liberata....

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_approve_a_refund_request_with_template_preview_for_letter() {

        final String paymentReference = createPayment();

        final String refundReference = performRefundWithLetter(paymentReference);

        final RefundReviewRequest  refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
            .code("RE001")
            .reason("Wrong Data")
            .templatePreview(TemplatePreview.templatePreviewWith()
                                 .id(UUID.randomUUID())
                                 .templateType("email")
                                 .version(11)
                                 .body("Dear Sir Madam")
                                 .subject("HMCTS refund request approved")
                                 .html("Dear Sir Madam")
                                 .from(FromTemplateContact
                                           .buildFromTemplateContactWith()
                                           .fromMailAddress(
                                               MailAddress
                                                   .buildRecipientMailAddressWith()
                                                   .addressLine("6 Test")
                                                   .city("city")
                                                   .country("country")
                                                   .county("county")
                                                   .postalCode("HA3 5TT")
                                                   .build())
                                           .build())
                                 .build())
            .build();

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            refundReviewRequest
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Approved")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Sent to middle office")
                    || entry.get("notes").trim().equals("Refund initiated and sent to team leader"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        //Should be verified as a call out to Liberata....

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_unknown_action_refund_request() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_unauthorized_user_refund_request() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_resubmit_refund_journey() {

        final String paymentReference = createPayment();
        final String accountNumber = testConfigProperties.existingAccountNumber;

        final String refundReference = performRefund(paymentReference);

        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );
        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        //Do a verification check so that the Payment App not has the remission amount of 80.00
        // not the initial 90.00
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
            = paymentsResponse.getPayments().stream().sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();

        assertThat(paymentDtoOptional.get().getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentDtoOptional.get().getAmount()).isEqualTo(new BigDecimal("90.00"));
        final String paymentReference1 = paymentDtoOptional.get().getReference();

        // delete payment records
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then()
            .statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void nagative_resubmit_refund_journey_without_service_role() {

        final String paymentReference = createPayment();
        final String accountNumber = testConfigProperties.existingAccountNumber;

        final String refundReference = performRefund(paymentReference);

        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );
        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(CONFLICT.value());

        // delete payment records
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then()
            .statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    private String createPayment() {
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        return paymentsResponse.getReference();
    }

    private String createPayment(String ccdCaseNumber) {
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        return paymentsResponse.getReference();
    }

    @NotNull
    private String performRefund(String paymentReference) {
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", paymentId);
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
    private String performRefundWithLetter(String paymentReference) {

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequestWithLetter("RR001", paymentReference, "90", "550", paymentId);
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
    private String performRefund2Fees(String paymentReference) {

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId1 = getPaymentsResponse.getFees().get(0).getId();
        int paymentId2 = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest2Fees("RR001", paymentReference, "90",
                                                "550", paymentId1, paymentId2);
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
    private String performRefundByApprover() {
        // Create a PBA payment
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001",
                                           paymentReference, "90.00",
                                           "550", paymentId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
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

    @Test
    public void positive_resubmit_refund_journey_when_amount_provided() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );
        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final Response refundListResponseAfterUpdate = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                        SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                        "Sent for approval", "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getAmount()).isEqualTo(new BigDecimal("10.00"));

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_resubmit_refund_journey_when_reason_provided() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);
        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );

        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponseAfterUpdate = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                        SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                        "Sent for approval", "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getReason()).isEqualTo("Amended court");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_resubmit_refund_journey_when_contactDetails_provided() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );

        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponseAfterUpdate = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                        SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                        "Sent for approval", "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getContactDetails().getEmail()).isEqualTo("testperson@somemail.com");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_not_change_reason_when_retro_remission_input_provided() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

        final Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest
                .buildRefundReviewRequest()
                .code("RE004")
                .reason("More evidence is required")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Update required", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        final RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        final String refundReferenceFromRefundList = optionalRefundDto.orElseThrow().getRefundReference();
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReferenceFromRefundList
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Sent for approval")
                    || entry.get("status").trim().equals("Update required"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                    || entry.get("notes").trim().equals("More evidence is required"))
                .isTrue();
        });//The Lifecycle of Statuses against a Refund will be maintained for all the statuses should be checked
        Response resubmitRefundResponse = paymentTestService.resubmitRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            RefundsFixture.resubmitRefundAllInput(),
            refundReferenceFromRefundList
        );

        assertThat(resubmitRefundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());

        final Response refundListResponseAfterUpdate = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                                        SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                                        "Sent for approval", "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getReason()).isEqualTo("Amended court");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_when_refund_cancelled_then_not_allow_refund_approve() {
        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);
        Response cancelResponse = paymentTestService.patchCancelRefunds(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentReference);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund is not submitted");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then()
            .statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_when_refund_cancelled_then_not_allow_refund_reject() {
        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

        //This API Request tests the Retrieve Actions endpoint as well.
        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<RefundEvent> refundEvents = response.getBody().jsonPath().get("$");
        assertThat(refundEvents.size()).isEqualTo(3);
        Response cancelResponse = paymentTestService.patchCancelRefunds(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentReference);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(BAD_REQUEST.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund is not submitted");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then()
            .statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_reject_a_refund_request_verify_contact_details_erased_from_service() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        // verify that contact details is registered
        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       "Sent for approval", "false");
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        Assert.assertNotNull(optionalRefundDto.get().getContactDetails());

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


        // verify that contact details is erased
        refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                              SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                              "Rejected", "false");
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        optionalRefundDto = refundsListDto.getRefundList().stream().sorted((s1, s2) ->
                                                                               s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();

        Assert.assertNull(optionalRefundDto.get().getContactDetails());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_V2Api_response_date_range() {

        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();

        final String refundReference = performRefund(paymentReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("start_date", getReportDate(new Date(System.currentTimeMillis())));
        params.add("end_date", getReportDate(new Date(System.currentTimeMillis())));
        Response response1 = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(ContentType.JSON)
            .params(params)
            .when()
            .get("/refunds");

        RerfundLiberataResponse rerfundLiberataResponse =  response1.getBody().as(RerfundLiberataResponse.class);;
        RefundLiberata refundLiberata = rerfundLiberataResponse.getRefunds().stream()
            .filter(rf -> rf.getReference().equals(refundReference)).findFirst().get();

        String refundApproveDate = getReportDate(refundLiberata.getDateApproved());
        String paymentDateCreated = getReportDate(refundLiberata.getPayment().getDateReceiptCreated());
        String date = getReportDate(new Date(System.currentTimeMillis()));
        assertThat("Amended claim").isEqualTo(refundLiberata.getReason());
        assertThat("SendRefund").isEqualTo(refundLiberata.getInstructionType());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getTotalRefundAmount());
        assertThat(date).isEqualTo(refundApproveDate);
        assertThat(date).isEqualTo(paymentDateCreated);
        assertThat("Probate").isEqualTo(refundLiberata.getPayment().getServiceName());
        assertThat("ABA6").isEqualTo(refundLiberata.getPayment().getSiteId());
        assertThat("online").isEqualTo(refundLiberata.getPayment().getChannel());
        assertThat("payment by account").isEqualTo(refundLiberata.getPayment().getMethod());
        assertThat(paymentResponse.getCcdCaseNumber()).isEqualTo(refundLiberata.getPayment().getCcdCaseNumber());
        assertThat("aCaseReference").isEqualTo(refundLiberata.getPayment().getCaseReference());
        assertThat("CUST101").isEqualTo(refundLiberata.getPayment().getCustomerReference());
        assertThat("PBAFUNC12345").isEqualTo(refundLiberata.getPayment().getPbaNumber());
        assertThat("FEE0001").isEqualTo(refundLiberata.getFees().get(0).getCode());
        assertThat("4481102133").isEqualTo(refundLiberata.getFees().get(0).getNaturalAccountCode());
        assertThat("1").isEqualTo(refundLiberata.getFees().get(0).getVersion());
        assertThat("civil").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction1());
        assertThat("county court").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction2());
        assertThat("GOV - Paper fees - Money claim >£200,000").isEqualTo(refundLiberata.getFees().get(0).getMemoLine());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getFees().get(0).getCredit());
        assertThat(new BigDecimal("10.00")).isEqualTo(refundLiberata.getPayment().getAvailableFunds());
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    private String getReportDate(Date date) {
        java.time.format.DateTimeFormatter reportNameDateFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date == null ? null : java.time.LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(reportNameDateFormat);
    }

    private PaymentDto createPaymentForV2Api() {
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "100.00",
                "PROBATE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        return paymentsResponse;
    }

    private PaymentDto createPaymentForV2Api2Fees() {
        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate2Fees(
                "100.00",
                "PROBATE",
                accountNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        return paymentsResponse;
    }



    @Test
    public void negative_return_400_V2Api_date_range_not_supported() {
        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();

        final String refundReference = performRefund(paymentReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Date date = new Date();
        LocalDateTime localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        localDateTime = localDateTime.plusDays(8);
        Date currentDatePlusOneDay = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("start_date", getReportDate(new Date(System.currentTimeMillis())));
        params.add("end_date", getReportDate(currentDatePlusOneDay));
        Response response = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(ContentType.JSON)
            .params(params)
            .when()
            .get("/refunds");

        assertThat(400).isEqualTo(response.getStatusCode());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void negative_return_413_V2Api_date_range_not_supported() {
        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();

        final String refundReference = performRefund(paymentReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Date date = new Date();
        LocalDateTime localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        localDateTime = localDateTime.minusDays(8);
        Date currentDatePlusOneDay = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("start_date", getReportDate(currentDatePlusOneDay));
        params.add("end_date", getReportDate(new Date(System.currentTimeMillis())));
        Response response = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(ContentType.JSON)
            .params(params)
            .when()
            .get("/refunds");

        assertThat(413).isEqualTo(response.getStatusCode());

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_V2Api_response_date_range_2Fees() {

        PaymentDto paymentResponse = createPaymentForV2Api2Fees();
        final String paymentReference = paymentResponse.getReference();

        final String refundReference = performRefund2Fees(paymentReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("start_date", getReportDate(new Date(System.currentTimeMillis())));
        params.add("end_date", getReportDate(new Date(System.currentTimeMillis())));
        Response response1 = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(ContentType.JSON)
            .params(params)
            .when()
            .get("/refunds");

        RerfundLiberataResponse rerfundLiberataResponse =  response1.getBody().as(RerfundLiberataResponse.class);;
        RefundLiberata refundLiberata = rerfundLiberataResponse.getRefunds().stream()
            .filter(rf -> rf.getReference().equals(refundReference)).findFirst().get();

        String refundApproveDate = getReportDate(refundLiberata.getDateApproved());
        String paymentDateCreated = getReportDate(refundLiberata.getPayment().getDateReceiptCreated());
        String date = getReportDate(new Date(System.currentTimeMillis()));
        assertThat("Amended claim").isEqualTo(refundLiberata.getReason());
        assertThat("SendRefund").isEqualTo(refundLiberata.getInstructionType());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getTotalRefundAmount());
        assertThat(date).isEqualTo(refundApproveDate);
        assertThat(date).isEqualTo(paymentDateCreated);
        assertThat("Probate").isEqualTo(refundLiberata.getPayment().getServiceName());
        assertThat("ABA6").isEqualTo(refundLiberata.getPayment().getSiteId());
        assertThat("online").isEqualTo(refundLiberata.getPayment().getChannel());
        assertThat("payment by account").isEqualTo(refundLiberata.getPayment().getMethod());
        assertThat(paymentResponse.getCcdCaseNumber()).isEqualTo(refundLiberata.getPayment().getCcdCaseNumber());
        assertThat("aCaseReference").isEqualTo(refundLiberata.getPayment().getCaseReference());
        assertThat("CUST101").isEqualTo(refundLiberata.getPayment().getCustomerReference());
        assertThat("PBAFUNC12345").isEqualTo(refundLiberata.getPayment().getPbaNumber());
        assertThat("FEE0001").isEqualTo(refundLiberata.getFees().get(0).getCode());
        assertThat("4481102133").isEqualTo(refundLiberata.getFees().get(0).getNaturalAccountCode());
        assertThat("1").isEqualTo(refundLiberata.getFees().get(0).getVersion());
        assertThat("civil").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction1());
        assertThat("county court").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction2());
        assertThat("GOV - Paper fees - Money claim >£200,000").isEqualTo(refundLiberata.getFees().get(0).getMemoLine());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getFees().get(0).getCredit());
        assertThat(new BigDecimal("10.00")).isEqualTo(refundLiberata.getPayment().getAvailableFunds());
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_V2Api_response_refund_reference() {

        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();

        final String refundReference = performRefund(paymentReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        params.add("refund_reference", refundReference);
        Response response1 = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN_PAY_BUBBLE_PAYMENT)
            .contentType(ContentType.JSON)
            .params(params)
            .when()
            .get("/refunds");

        RerfundLiberataResponse rerfundLiberataResponse =  response1.getBody().as(RerfundLiberataResponse.class);;
        RefundLiberata refundLiberata = rerfundLiberataResponse.getRefunds().stream()
            .filter(rf -> rf.getReference().equals(refundReference)).findFirst().get();

        String refundApproveDate = getReportDate(refundLiberata.getDateApproved());
        String paymentDateCreated = getReportDate(refundLiberata.getPayment().getDateReceiptCreated());
        String date = getReportDate(new Date(System.currentTimeMillis()));
        assertThat("Amended claim").isEqualTo(refundLiberata.getReason());
        assertThat("SendRefund").isEqualTo(refundLiberata.getInstructionType());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getTotalRefundAmount());
        assertThat(date).isEqualTo(refundApproveDate);
        assertThat(date).isEqualTo(paymentDateCreated);
        assertThat("Probate").isEqualTo(refundLiberata.getPayment().getServiceName());
        assertThat("ABA6").isEqualTo(refundLiberata.getPayment().getSiteId());
        assertThat("online").isEqualTo(refundLiberata.getPayment().getChannel());
        assertThat("payment by account").isEqualTo(refundLiberata.getPayment().getMethod());
        assertThat(paymentResponse.getCcdCaseNumber()).isEqualTo(refundLiberata.getPayment().getCcdCaseNumber());
        assertThat("aCaseReference").isEqualTo(refundLiberata.getPayment().getCaseReference());
        assertThat("CUST101").isEqualTo(refundLiberata.getPayment().getCustomerReference());
        assertThat("PBAFUNC12345").isEqualTo(refundLiberata.getPayment().getPbaNumber());
        assertThat("FEE0001").isEqualTo(refundLiberata.getFees().get(0).getCode());
        assertThat("4481102133").isEqualTo(refundLiberata.getFees().get(0).getNaturalAccountCode());
        assertThat("1").isEqualTo(refundLiberata.getFees().get(0).getVersion());
        assertThat("civil").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction1());
        assertThat("county court").isEqualTo(refundLiberata.getFees().get(0).getJurisdiction2());
        assertThat("GOV - Paper fees - Money claim >£200,000").isEqualTo(refundLiberata.getFees().get(0).getMemoLine());
        assertThat(new BigDecimal("90.00")).isEqualTo(refundLiberata.getFees().get(0).getCredit());
        assertThat(new BigDecimal("10.00")).isEqualTo(refundLiberata.getPayment().getAvailableFunds());
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    @Test
    public void positive_get_refund_list_for_only_payment_role() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();

        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90", "550", paymentId);

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

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_CMC_CASE_WORKER_WITH_PAYMENT_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getDescription())
            .isEqualTo("Refund request submitted");

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refunds records
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);

    }

    @Test
    public void negative_get_refund_list_when_no_sufficient_role_refund() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
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
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,"5",
                                                                              testConfigProperties.basePaymentsUrl);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int paymentId = getPaymentsResponse.getFees().get(0).getId();

        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90", "550", paymentId);
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

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(FORBIDDEN.value());
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentDto.getReference(),
                           testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refunds records
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);

    }


    @Test
    public void positive_refund_reject_reason_unable_to_apply_refund_to_card_and_email() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        //reject refund with instruction type refundWhenContacted with reason unable to apply refund to card
        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest.RefundRequestWith().reason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)
                .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build()
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that contact details is erased
        RefundDto refundDto = getRejectRefundDto(refundReference, "Rejected");
        assertEquals(RefundStatus.REJECTED, refundDto.getRefundStatus());
        assertEquals("Amended claim", refundDto.getReason());

        deletePaymentAndRefund(paymentReference, refundReference);
    }

    @Test
    public void positive_refund_reject_reason_with_different_reason_and_email() {

        final String paymentReference = createPayment();

        final String refundReference = performRefund(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        //reject refund with instruction type refundWhenContacted with reason unable to apply refund to card
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Different Reason",
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED
        );

        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            refundStatusUpdateRequest
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that contact details is erased
        RefundDto refundDto = getRejectRefundDto(refundReference, "Rejected");
        assertEquals(RefundStatus.REJECTED, refundDto.getRefundStatus());
        assertNotEquals("Unable to apply refund to Card", refundDto.getReason());

        deletePaymentAndRefund(paymentReference, refundReference);
    }

    @Test
    public void positive_refund_reject_reason_with_different_reason_and_letter() {

        final String paymentReference = createPayment();

        final String refundReference = performRefundWithLetter(paymentReference);

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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        //reject refund with instruction type refundWhenContacted with reason unable to apply refund to card
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Different Reason",
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED
        );

        Response updateRefundStatusResponse = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            refundStatusUpdateRequest
        );
        assertThat(updateRefundStatusResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // verify that contact details is erased

        RefundDto refundDto = getRejectRefundDto(refundReference,"Rejected");
        assertEquals(RefundStatus.REJECTED, refundDto.getRefundStatus());
        assertNotEquals("Unable to apply refund to Card", refundDto.getReason());

        deletePaymentAndRefund(paymentReference, refundReference);
    }

    private RefundDto getRejectRefundDto(String refundReference, String status) {

        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       status, "false");
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);

        RefundDto refundDto = refundsListDto.getRefundList().stream()
            .filter(rf -> rf.getRefundReference().equals(refundReference)).findFirst().get();

        return refundDto;
    }

    private void deletePaymentAndRefund(String paymentReference, String refundReference) {
        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference);
    }

    private String generateCcdCaseNumber() {

        Random rand = new Random();
        String ccdCaseNumber = String.format((Locale)null, //don't want any thousand separators
                                             "%04d22%04d%04d%02d",
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(99));

        return ccdCaseNumber;
    }
}
