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
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureReportDtoResponse;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.functional.fixture.RefundsFixture;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.response.PaymentDto;
import uk.gov.hmcts.reform.refunds.functional.response.RefundResponse;
import uk.gov.hmcts.reform.refunds.functional.service.PaymentTestService;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
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
    private static String SERVICE_TOKEN_CMC;
    private static boolean TOKENS_INITIALIZED;
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
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE =
                idamService.createUserWithSearchScope(
                        IdamService.CMC_CASE_WORKER_GROUP,
                        "payments-refund-approver",
                        "payments"
                    )
                    .getAuthorisationToken();

            SERVICE_TOKEN_CMC =
                s2sTokenService.getS2sToken(testConfigProperties.cmcS2SName, testConfigProperties.cmcS2SSecret);

            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT =
                s2sTokenService.getS2sToken("ccpay_bubble", testConfigProperties.payBubbleS2SSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    @Ignore
    public void payment_failure_report_request() {
        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);

        PaymentFailureReportDtoResponse paymentFailureReportDtoResponse =
            paymentTestService.getPaymentFailureReport(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
                                                       SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, paymentReference
                ).then()
                .statusCode(OK.value()).extract().as(PaymentFailureReportDtoResponse.class);

        assertEquals(1, paymentFailureReportDtoResponse.getPaymentFailureDto().size());
        assertEquals(paymentReference,
                     paymentFailureReportDtoResponse.getPaymentFailureDto().get(0).getPaymentReference());
        assertEquals(
            refundReference,
            paymentFailureReportDtoResponse.getPaymentFailureDto().get(0).getRefundReference()
        );

        // delete payment record
        paymentTestService
            .deletePayment(USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                           paymentReference, testConfigProperties.basePaymentsUrl
            ).then()
            .statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                        refundReference
        );
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
        return paymentsResponse.getReference();
    }

    @NotNull
    private String performRefund(String paymentReference) {
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference);
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

}
