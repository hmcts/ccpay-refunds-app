package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.AfterClass;
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
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberataResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.functional.config.ValidUser;
import uk.gov.hmcts.reform.refunds.functional.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.response.PaymentDto;
import uk.gov.hmcts.reform.refunds.functional.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.functional.service.PaymentTestService;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static String USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE;
    private static String USER_TOKEN_WITH_NO_SEARCH_SCOPE_PAYMENTS_ROLE;
    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE;
    private static String USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE;
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

            ValidUser user4 = idamService.createUserWithSearchScope("payments-refund-approver",
                                                                    "payments-refund-probate",
                                                                    "payments-refund-approver-probate"
            );
            USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE = user4.getAuthorisationToken();
            userEmails.add(user4.getEmail());

            ValidUser user5 = idamService.createUserWithSearchScope("payments-refund");
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE = user5.getAuthorisationToken();
            userEmails.add(user5.getEmail());

            ValidUser user6 = idamService.createUserWithSearchScope("payments-refund-approver");
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE = user6.getAuthorisationToken();
            userEmails.add(user6.getEmail());

            ValidUser user7 = idamService.createUserWithSearchScope("payments-refund-approver",
                                                                    "payments-refund-approver-divorce",
                                                                    "payments-refund-approver-probate",
                                                                    "payments"
            );
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE = user7.getAuthorisationToken();
            userEmails.add(user7.getEmail());

            ValidUser user8 = idamService.createUserWithSearchScope("payments");
            USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE = user8.getAuthorisationToken();
            userEmails.add(user8.getEmail());

            ValidUser user9 = idamService.createUserWith("payments");
            USER_TOKEN_WITH_NO_SEARCH_SCOPE_PAYMENTS_ROLE = user9.getAuthorisationToken();
            userEmails.add(user9.getEmail());

            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);

            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);

            TOKENS_INITIALIZED = true;
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
    public void negative_get_refund_list_for_case_with_no_refunds() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        final String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber,
                ccdCaseNumber
            );
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);

        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(NO_CONTENT.value());
    }

    @Test
    public void positive_reject_a_refund_request() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        String paymentReference = paymentsResponse.getReference();
        paymentReferences.add(paymentReference);

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );

        assertThat(refundResponse.getStatusCode()).isEqualTo(BAD_REQUEST.value());
    }

    @Test
    public void positive_sendback_a_refund_request() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund returned to caseworker");
    }

    @Test
    public void positive_sendback_a_refund_request_without_service_role() {

        final String paymentReference = createPayment();
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.SENDBACK.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CONFLICT.value());
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        paymentReferences.add(paymentReference);
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90.00", "550", feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        refundReferences.add(refundReference);
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
        paymentReferences.add(paymentDto1.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(), "90", "550", feeId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        refundReferences.add(refundReference1);
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
        paymentReferences.add(paymentDto2.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(), "90", "550", feeId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        refundReferences.add(refundReference2);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference2).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertEquals(refundListDtoResponse.getRefundList().size(), 3);
    }

    @Test
    public void positive_get_refund_only_for_probate_service_role() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = generateCcdCaseNumber();

        // Create Payment 1
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequest(
                "PROBATE",
                "ABA6",
                accountNumber,
                ccdCaseNumber,
                "273",
                "FEE0219",
                "5"
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("273.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        paymentReferences.add(paymentReference);
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90.00", "273", "FEE0219", "5", feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        refundReferences.add(refundReference);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);

        // Create Payment 2
        final CreditAccountPaymentRequest accountPaymentRequest1 = RefundsFixture
            .pbaPaymentRequest(
                "DIVORCE",
                "ABA5",
                accountNumber,
                ccdCaseNumber,
                "550",
                "FEE0002",
                "6"
            );
        accountPaymentRequest1.setAccountNumber(accountNumber);
        PaymentDto paymentDto1 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest1
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);
        paymentReferences.add(paymentDto1.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(), "90.00", "550", "FEE0002", "6", feeId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        refundReferences.add(refundReference1);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference1).matches()).isEqualTo(true);

        // Create Payment 3
        final CreditAccountPaymentRequest accountPaymentRequest2 = RefundsFixture
            .pbaPaymentRequest(
                "DIVORCE",
                "ABA5",
                accountNumber,
                ccdCaseNumber,
                "43",
                "FEE0002",
                "6"
            );
        accountPaymentRequest2.setAccountNumber(accountNumber);
        PaymentDto paymentDto2 = paymentTestService.postPbaPayment(
                USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                SERVICE_TOKEN_CMC,
                testConfigProperties.basePaymentsUrl,
                accountPaymentRequest2
            ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);
        paymentReferences.add(paymentDto2.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(), "10.00", "43", "FEE0002", "6", feeId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        refundReferences.add(refundReference2);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference2).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_ROLE_WITH_PROBATE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Sent for approval",
                                                                             "false"
        );

        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        for (RefundDto refundDto : refundListDtoResponse.getRefundList()) {
            assertEquals("Probate", refundDto.getServiceType());
        }
    }

    @Test
    public void positive_get_refund_list_for_an_approver() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

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
        paymentReferences.add(paymentDto.getReference());
        // Get pba payment by reference
        PaymentDto paymentsResponse =
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90.00", "550", feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        refundReferences.add(refundReference);
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
        paymentReferences.add(paymentDto1.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );
        PaymentDto getPaymentsResponse1 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto1.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId1 = getPaymentsResponse1.getFees().get(0).getId();
        // Create Refund 2
        final PaymentRefundRequest paymentRefundRequest1
            = RefundsFixture.refundRequest("RR001", paymentDto1.getReference(), "90", "550", feeId1);
        Response refundResponse1 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest1,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost1 = refundResponse1.getBody().as(RefundResponse.class);
        final String refundReference1 = refundResponseFromPost1.getRefundReference();
        refundReferences.add(refundReference1);
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
        paymentReferences.add(paymentDto2.getReference());
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse2 =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto2.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId2 = getPaymentsResponse2.getFees().get(0).getId();
        // Create Refund 3
        final PaymentRefundRequest paymentRefundRequest2
            = RefundsFixture.refundRequest("RR001", paymentDto2.getReference(), "90", "550", feeId2);
        Response refundResponse2 = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest2,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost2 = refundResponse2.getBody().as(RefundResponse.class);
        final String refundReference2 = refundResponseFromPost2.getRefundReference();
        refundReferences.add(refundReference2);
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
    }

    @Test
    public void negative_approver_can_request_refund_but_not_self_approve() {

        final String refundReference = performRefundByApprover();
        refundReferences.add(refundReference);
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE004")
                .reason("More evidence is required").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(FORBIDDEN.value());
    }

    @Test
    public void positive_approve_a_refund_request() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
    }

    @Test
    public void nagative_approve_a_refund_request_without_service_role() {

        final String paymentReference = createPayment();
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE_WITHOUT_SERVICE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CONFLICT.value());
    }

    @Test
    public void positive_approve_a_refund_request_with_template_preview_for_email() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

        final RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
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
    }

    @Test
    public void positive_approve_a_refund_request_with_template_preview_for_letter() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefundWithLetter(paymentReference);
        refundReferences.add(refundReference);

        final RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
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
    }

    @Test
    public void negative_unknown_action_refund_request() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
            "NO DEFINED STATUS",
            RefundReviewRequest.buildRefundReviewRequest()
                .code("RE003")
                .reason("The case details don’t match the help with fees details")
                .build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(BAD_REQUEST.value());

    }

    @Test
    public void negative_unauthorized_user_refund_request() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
            USER_TOKEN_WITH_NO_SEARCH_SCOPE_PAYMENTS_ROLE,
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
    public void positive_resubmit_refund_journey() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        final String accountNumber = testConfigProperties.existingAccountNumber;
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
                                                                             ccdCaseNumber, "Update required", "false"
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
        // Get payment by RC reference
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentReference).isEqualTo(getPaymentsResponse.getPaymentReference());
        assertThat(accountNumber).isEqualTo(getPaymentsResponse.getAccountNumber());
        assertThat(new BigDecimal("90.00")).isEqualTo(getPaymentsResponse.getAmount());
    }

    @Test
    public void nagative_resubmit_refund_journey_without_service_role() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
                                                                             ccdCaseNumber, "Update required", "false"
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        return paymentsResponse.getReference();
    }

    private String createPayment(String ccdCaseNumber) {
        final String accountNumber = testConfigProperties.existingAccountNumber;
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
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
    private String performRefund(String paymentReference) {
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", feeId);
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
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequestWithLetter("RR001", paymentReference, "90", "550", feeId);
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
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId1 = getPaymentsResponse.getFees().get(0).getId();
        int feeId2 = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest2Fees("RR001", paymentReference, "90",
                                                "550", feeId1, feeId2
        );
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
        paymentReferences.add(paymentDto.getReference());
        // Get pba payment by reference
        PaymentDto paymentsResponse =
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001",
                                           paymentReference, "90.00",
                                           "550", feeId
        );
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
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
                                                                             ccdCaseNumber, "Update required", "false"
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
                                                                                        ccdCaseNumber,
                                                                                        "Sent for approval",
                                                                                        "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(
            RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getAmount()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    public void positive_resubmit_refund_journey_when_reason_provided() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);
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
                                                                             ccdCaseNumber, "Update required", "false"
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
                                                                                        ccdCaseNumber,
                                                                                        "Sent for approval",
                                                                                        "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(
            RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getReason()).isEqualTo("Amended court");
    }

    @Test
    public void positive_resubmit_refund_journey_when_contactDetails_provided() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
                                                                             ccdCaseNumber, "Update required", "false"
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
                                                                                        ccdCaseNumber,
                                                                                        "Sent for approval",
                                                                                        "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(
            RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getContactDetails().getEmail()).isEqualTo(
            "testperson@somemail.com");
    }

    @Test
    public void negative_not_change_reason_when_retro_remission_input_provided() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
                                                                             ccdCaseNumber, "Update required", "false"
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
                                                                                        ccdCaseNumber,
                                                                                        "Sent for approval",
                                                                                        "false"
        );

        final RefundListDtoResponse refundsListDtosAfterUpdate = refundListResponseAfterUpdate.getBody().as(
            RefundListDtoResponse.class);

        Optional<RefundDto> optionalRefundDtoAfterUpdate = refundsListDtosAfterUpdate.getRefundList().stream()
            .sorted((s1, s2) -> s2.getDateUpdated().compareTo(s1.getDateUpdated())).findFirst();

        assertThat(optionalRefundDtoAfterUpdate.get().getReason()).isEqualTo("Amended court");
    }

    @Test
    public void negative_when_refund_cancelled_then_not_allow_refund_approve() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
        Response cancelResponse = paymentTestService.patchCancelRefunds(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentReference
        );
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
    }

    @Test
    public void negative_when_refund_cancelled_then_not_allow_refund_reject() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
        Response cancelResponse = paymentTestService.patchCancelRefunds(
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentReference
        );
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
    }

    @Test
    public void positive_reject_a_refund_request_verify_contact_details_erased_from_service() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

        Response response = paymentTestService.getRetrieveActions(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        // verify that contact details is registered
        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       ccdCaseNumber, "Sent for approval", "false"
        );
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
                                                              ccdCaseNumber, "Rejected", "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        optionalRefundDto = refundsListDto.getRefundList().stream().sorted((s1, s2) ->
                                                                               s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();

        Assert.assertNull(optionalRefundDto.get().getContactDetails());
    }

    @Test
    public void positive_V2Api_response_date_range() throws InterruptedException {

        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();
        paymentReferences.add(paymentReference);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        String startDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("start_date", startDate);

        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Thread.sleep(2000);
        String endDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("end_date", endDate);

        Response response = paymentTestService.getRefunds(SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, params);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        RefundLiberataResponse refundLiberataResponse = response.getBody().as(RefundLiberataResponse.class);
        RefundLiberata refundLiberata = refundLiberataResponse.getRefunds().stream()
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
    }

    private String getReportDate(Date date) {
        java.time.format.DateTimeFormatter reportNameDateFormat = java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd");
        return date == null ? null : java.time.LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(
            reportNameDateFormat);
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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

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
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());

        // Update Payments for CCDCaseNumber by certain days
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        return paymentsResponse;
    }


    @Test
    public void negative_return_400_V2Api_date_range_not_supported() {
        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
        Response response = paymentTestService.getRefunds(SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, params);

        assertThat(400).isEqualTo(response.getStatusCode());
    }

    @Test
    public void negative_return_413_V2Api_date_range_not_supported() {
        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

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
        Response response = paymentTestService.getRefunds(SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, params);

        assertThat(413).isEqualTo(response.getStatusCode());
    }

    @Test
    public void positive_V2Api_response_date_range_2Fees() throws InterruptedException {

        PaymentDto paymentResponse = createPaymentForV2Api2Fees();
        final String paymentReference = paymentResponse.getReference();
        paymentReferences.add(paymentReference);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        String startDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("start_date", startDate);

        final String refundReference = performRefund2Fees(paymentReference);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Thread.sleep(2000);
        String endDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("end_date", endDate);

        Response response = paymentTestService.getRefunds(SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, params);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        RefundLiberataResponse refundLiberataResponse = response.getBody().as(RefundLiberataResponse.class);
        RefundLiberata refundLiberata = refundLiberataResponse.getRefunds().stream()
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
    }

    @Test
    public void positive_V2Api_response_refund_reference() throws InterruptedException {

        PaymentDto paymentResponse = createPaymentForV2Api();
        final String paymentReference = paymentResponse.getReference();
        paymentReferences.add(paymentReference);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        String startDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("start_date", startDate);

        final String refundReference = performRefund(paymentReference);
        refundReferences.add(refundReference);

        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            ReviewerAction.APPROVE.name(),
            RefundReviewRequest.buildRefundReviewRequest().code("RE001").reason("Wrong Data").build()
        );
        assertThat(responseReviewRefund.getStatusCode()).isEqualTo(CREATED.value());
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Thread.sleep(2000);
        String endDate = org.joda.time.LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        params.add("end_date", endDate);
        params.add("refund_reference", refundReference);
        Response response = paymentTestService.getRefunds(SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, params);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());

        RefundLiberataResponse refundLiberataResponse = response.getBody().as(RefundLiberataResponse.class);

        RefundLiberata refundLiberata = refundLiberataResponse.getRefunds().stream()
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
    }

    @Test
    public void positive_get_refund_list_for_only_payment_role() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        final String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

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
        paymentReferences.add(paymentDto.getReference());
        // Get pba payment by reference
        PaymentDto paymentsResponse =
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );
        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();

        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", feeId);

        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        refundReferences.add(refundReference);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_WITH_SEARCH_SCOPE_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getDescription())
            .isEqualTo("Refund request submitted");
    }

    @Test
    public void negative_get_refund_list_when_no_sufficient_role_refund() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        final String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

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
        paymentReferences.add(paymentDto.getReference());
        // Get pba payment by reference
        PaymentDto paymentsResponse =
            paymentTestService.getPbaPayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentDto.getReference(),
                                             testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getReference();
        // Update Payments for CCDCaseNumber by certain days
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                                                              SERVICE_TOKEN_CMC,
                                                                              ccdCaseNumber,
                                                                              "5",
                                                                              testConfigProperties.basePaymentsUrl
        );

        PaymentDto getPaymentsResponse =
            paymentTestService.getPayments(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                           SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference,
                                           testConfigProperties.basePaymentsUrl
                ).then()
                .statusCode(OK.value()).extract().as(PaymentDto.class);

        int feeId = getPaymentsResponse.getFees().get(0).getId();

        // Create Refund 1
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "550", feeId);
        Response refundResponse = paymentTestService.postInitiateRefund(
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            paymentRefundRequest,
            testConfigProperties.basePaymentsUrl
        );
        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        final RefundResponse refundResponseFromPost = refundResponse.getBody().as(RefundResponse.class);
        final String refundReference = refundResponseFromPost.getRefundReference();
        refundReferences.add(refundReference);
        assertThat(REFUNDS_REGEX_PATTERN.matcher(refundReference).matches()).isEqualTo(true);

        // Fetch refunds based on CCD Case Number
        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ccdCaseNumber
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(FORBIDDEN.value());
    }

    @Test
    public void positive_refund_reject_reason_with_different_reason_and_email() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefund(paymentReference);
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
        RefundDto refundDto = getRejectRefundDto(ccdCaseNumber, refundReference, "Rejected");
        assertEquals(RefundStatus.REJECTED, refundDto.getRefundStatus());
        assertNotEquals("Unable to apply refund to Card", refundDto.getReason());
    }

    @Test
    public void positive_refund_reject_reason_with_different_reason_and_letter() {
        String ccdCaseNumber = "11111234" + RandomUtils.secure().randomInt(CCD_EIGHT_DIGIT_LOWER, CCD_EIGHT_DIGIT_UPPER);

        final String paymentReference = createPayment(ccdCaseNumber);
        paymentReferences.add(paymentReference);
        final String refundReference = performRefundWithLetter(paymentReference);
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

        RefundDto refundDto = getRejectRefundDto(ccdCaseNumber, refundReference, "Rejected");
        assertEquals(RefundStatus.REJECTED, refundDto.getRefundStatus());
        assertNotEquals("Unable to apply refund to Card", refundDto.getReason());
    }

    private RefundDto getRejectRefundDto(String ccdCaseNumber, String refundReference, String status) {
        Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                       ccdCaseNumber, status, "false"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);

        return refundsListDto.getRefundList().stream()
            .filter(rf -> rf.getRefundReference().equals(refundReference)).findFirst().get();
    }

    private String generateCcdCaseNumber() {

        Random rand = new Random();
        return String.format(
            (Locale) null, //don't want any thousand separators
            "%04d22%04d%04d%02d",
            rand.nextInt(10000),
            rand.nextInt(10000),
            rand.nextInt(10000),
            rand.nextInt(99)
        );
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
                USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
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
