package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
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
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;


@ActiveProfiles({"functional", "liberataMock"})
@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest
public class PaymentFailureReportFunctionalTest {

    @Autowired
    private TestConfigProperties testConfigProperties;

    @Autowired
    private IdamService idamService;

    @Autowired
    private S2sTokenService s2sTokenService;

    @Inject
    private PaymentTestService paymentTestService;

    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String SERVICE_TOKEN_CMC;
    private String paymentReference;
    private String refundReference;
    private static List<String> userEmails = new ArrayList<>();
    private static boolean TOKENS_INITIALIZED;
    private static final Pattern
        REFUNDS_REGEX_PATTERN = Pattern.compile("^(RF)-([0-9]{4})-([0-9-]{4})-([0-9-]{4})-([0-9-]{4})$");

    @Before
    public void setUp() {

        RestAssured.baseURI = testConfigProperties.baseTestUrl;
        if (!TOKENS_INITIALIZED) {
            ValidUser user1 = idamService.createUserWith("caseworker-cmc-solicitor");
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE = user1.getAuthorisationToken();
            userEmails.add(user1.getEmail());

            ValidUser user2 = idamService.createUserWithSearchScope("payments-refund", "payments-refund-probate");
            USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE = user2.getAuthorisationToken();
            userEmails.add(user2.getEmail());

            ValidUser user3 = idamService.createUserWithSearchScope("payments-refund-approver", "payments-refund-approver-probate");
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE = user3.getAuthorisationToken();
            userEmails.add(user3.getEmail());

            ValidUser user4 = idamService.createUserWithSearchScope("payments-refund-approver", "payments");
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE = user4.getAuthorisationToken();
            userEmails.add(user4.getEmail());

            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);

            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void paymentFailureReportRequestForRejectedRefundStatus() {
        paymentReference = createPayment();
        refundReference = performRefund(paymentReference);

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

        paymentTestService.getPaymentFailureReport(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                                   SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference
            ).then()
            .statusCode(NOT_FOUND.value());
    }

    @Test
    public void paymentFailureReportRequestForEmptyPaymentReference() {

        paymentTestService.getPaymentFailureReport(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                                   SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, ""
            ).then()
            .statusCode(BAD_REQUEST.value());
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

        int feeId = getPaymentsResponse.getFees().get(0).getId();

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference,"90","550", feeId);
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
        if (!StringUtils.isBlank(refundReference)) {
            //delete refund record
            paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference);
        }
        if (!StringUtils.isBlank(paymentReference)) {
            // delete payment record
            paymentTestService.deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                             paymentReference, testConfigProperties.basePaymentsUrl);
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
