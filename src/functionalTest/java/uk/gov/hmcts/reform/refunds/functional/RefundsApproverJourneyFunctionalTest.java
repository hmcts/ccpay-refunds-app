package uk.gov.hmcts.reform.refunds.functional;

import io.restassured.RestAssured;
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
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
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
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

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
    @Autowired
    private RefundsRepository refundsRepository;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static String USER_TOKEN_CMC_CITIZEN_WITH_PAYMENT_ROLE;
    private static String SERVICE_TOKEN_PAY_BUBBLE_PAYMENT;
    private static String USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE;
    private static String USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE;
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

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver")
                    .getAuthorisationToken();

            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE =
                idamService.createUserWithSearchScope(IdamService.CMC_CASE_WORKER_GROUP, "payments-refund-approver", "payments")
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
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
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

        //Further verify that there is no invocation ofx Liberata

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_get_refund_list_for_an_approver() {

        final String refundReference = performRefundByApprover();
        final Response refundListResponse = paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
                                                                             "Sent for approval", "true"
        );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        refundListDtoResponse.getRefundList().forEach(refundDto -> assertThat(refundDto.getRefundReference()).isNotEqualTo(refundReference));

        RefundListDtoResponse refundsListDto = refundListResponse.getBody().as(RefundListDtoResponse.class);
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream()
            .sorted((s1, s2) ->
                        s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
        assertThat(optionalRefundDto.get().getContactDetails()).isNotNull();
        refundListDtoResponse.getRefundList().forEach(refundDto -> assertThat(refundDto.getRefundReference()).isNotEqualTo(refundReference));

        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);

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
            RefundReviewRequest.buildRefundReviewRequest().build()
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
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
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
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
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
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_resubmit_refund_journey() {

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

        //Do a verification check so that the Payment App not has the remission amount of 80.00
        // not the initial 90.00
        final String accountNumber = testConfigProperties.existingAccountNumber;
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
        //assertThat(paymentDtoOptional.get().getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference1 = paymentDtoOptional.get().getPaymentReference();

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference1).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_approval_from_liberata() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
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
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Response updateReviewRefund = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest
                .RefundRequestWith()
                .reason("Accepted")
                .status(RefundStatus.ACCEPTED).build()
        );
        assertThat(updateReviewRefund.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        Response refundStatusHistoryListResponse =
            paymentTestService.getStatusHistory(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                                SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference
            );
        assertThat(refundStatusHistoryListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        List<Map<String, String>> statusHistoryList =
            refundStatusHistoryListResponse.getBody().jsonPath().getList("status_history_dto_list");
        statusHistoryList.forEach(entry -> {
            assertThat(
                entry.get("status").trim().equals("Accepted")
                    || entry.get("status").trim().equals("Approved")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                entry.get("notes").trim().equals("Approved by middle office")
                    || entry.get("notes").trim().equals("Sent to middle office")
                    || entry.get("notes").trim().equals("Refund initiated and sent to team leader"))
                .isTrue();
        });

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void negative_double_approval_from_liberata() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
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
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");


        Response updateReviewRefund = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest
                .RefundRequestWith()
                .reason("Accepted")
                .status(RefundStatus.ACCEPTED).build()
        );

        assertThat(updateReviewRefund.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        Response updateReviewRefundAgain =
            paymentTestService.updateRefundStatus(USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
                                                  SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, refundReference,
                                                  RefundStatusUpdateRequest.RefundRequestWith()
                                                      .reason("Accepted")
                                                      .status(RefundStatus.ACCEPTED).build()
            );
        assertThat(updateReviewRefundAgain.getStatusCode()).isEqualTo(CONFLICT.value());
        assertThat(updateReviewRefundAgain.getBody().asString()).isEqualTo("Action not allowed to proceed");

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_rejected_from_liberata() {

        final String paymentReference = createPayment();
        final String refundReference = performRefund(paymentReference);
        Response responseReviewRefund = paymentTestService.patchReviewRefund(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_AND_PAYMENTS_ROLE,
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
        assertThat(responseReviewRefund.getBody().asString()).isEqualTo("Refund approved");

        Response updateReviewRefund = paymentTestService.updateRefundStatus(
            USER_TOKEN_PAYMENTS_REFUND_APPROVER_ROLE,
            SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference,
            RefundStatusUpdateRequest
                .RefundRequestWith()
                .reason("Rejected From Liberata - User Input")
                .status(RefundStatus.REJECTED).build()
        );
        assertThat(updateReviewRefund.getStatusCode()).isEqualTo(NO_CONTENT.value());
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
                    || entry.get("status").trim().equals("Approved")
                    || entry.get("status").trim().equals("Sent for approval"))
                .isTrue();
            assertThat(
                    entry.get("notes").trim().equals("Sent to middle office")
                        || entry.get("notes").trim().equals("Refund initiated and sent to team leader")
                        || entry.get("notes").trim().equals("Rejected From Liberata - User Input"))
                .isTrue();
        });

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentReference).then().statusCode(NO_CONTENT.value());
        // delete refund record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_refund_list_for_ccd_case_number_journey() {

        final String accountNumber = testConfigProperties.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest = RefundsFixture
            .pbaPaymentRequestForProbate(
                "90.00",
                "PROBATE",
                accountNumber
            );
        String ccdCaseNumber = accountPaymentRequest.getCcdCaseNumber();
        accountPaymentRequest.setAccountNumber(accountNumber);
        PaymentDto paymentDto = paymentTestService.postPbaPayment(
            USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE,
            SERVICE_TOKEN_CMC,
            testConfigProperties.basePaymentsUrl,
            accountPaymentRequest
        ).then()
            .statusCode(CREATED.value()).body("status", equalTo("Success")).extract().as(PaymentDto.class);
        paymentTestService.updateThePaymentDateByCcdCaseNumberForCertainHours(USER_TOKEN_ACCOUNT_WITH_SOLICITORS_ROLE, SERVICE_TOKEN_CMC,
                ccdCaseNumber,"5",
                testConfigProperties.basePaymentsUrl);

        // Get pba payments by accountNumber
        // Get pba payment by reference
        PaymentDto paymentsResponse =
                paymentTestService.getPbaPayment(USER_TOKEN, SERVICE_TOKEN, paymentDto.getReference()).then()
                        .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getPaymentReference();

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "0");
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

        final Response refundListResponse =
            paymentTestService.getRefundList(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE,
                                             SERVICE_TOKEN_PAY_BUBBLE_PAYMENT, accountPaymentRequest.getCcdCaseNumber()
            );
        assertThat(refundListResponse.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        RefundListDtoResponse refundListDtoResponse = refundListResponse.getBody().as(RefundListDtoResponse.class);
        assertThat(refundListDtoResponse.getRefundList().size()).isEqualTo(1);
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getName())
            .isEqualTo("Sent for approval");
        assertThat(refundListDtoResponse.getRefundList().get(0).getRefundStatus().getDescription())
            .isEqualTo("Refund request submitted");

        // delete payment record
        paymentTestService.deletePayment(USER_TOKEN, SERVICE_TOKEN, paymentDto.getReference()).then().statusCode(NO_CONTENT.value());
        //delete refunds record
        paymentTestService.deleteRefund(USER_TOKEN_PAYMENTS_REFUND_REQUESTOR_ROLE, SERVICE_TOKEN_PAY_BUBBLE_PAYMENT,
            refundReference);
    }

    @Test
    public void positive_reject_a_refund_request_verify_contact_details_erased_from_service() {

        final String refundReference = performRefund(createPayment());
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
        Optional<RefundDto> optionalRefundDto = refundsListDto.getRefundList().stream().sorted((s1, s2) ->
                s2.getDateCreated().compareTo(s1.getDateCreated())).findFirst();
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
                paymentTestService.getPbaPayment(USER_TOKEN, SERVICE_TOKEN, paymentDto.getReference()).then()
                        .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        return paymentsResponse.getPaymentReference();
    }

    @NotNull
    private String performRefund(String paymentReference) {
        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "0");
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
                paymentTestService.getPbaPayment(USER_TOKEN, SERVICE_TOKEN, paymentDto.getReference()).then()
                        .statusCode(OK.value()).extract().as(PaymentDto.class);

        assertThat(paymentsResponse.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(paymentsResponse.getAmount()).isEqualTo(new BigDecimal("90.00"));
        assertThat(paymentsResponse.getCcdCaseNumber()).isEqualTo(accountPaymentRequest.getCcdCaseNumber());
        final String paymentReference = paymentsResponse.getPaymentReference();

        final PaymentRefundRequest paymentRefundRequest
            = RefundsFixture.refundRequest("RR001", paymentReference, "90", "0");
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

        final String refundReference = performRefund(createPayment());
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
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

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

        assertThat(optionalRefundDtoAfterUpdate.get().getAmount()).isEqualTo(new BigDecimal("80.00"));

    }

    @Test
    public void positive_resubmit_refund_journey_when_reason_provided() {

        final String refundReference = performRefund(createPayment());
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
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

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

    }

    @Test
    public void positive_resubmit_refund_journey_when_contactDetails_provided() {

        final String refundReference = performRefund(createPayment());
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
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

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

    }

    @Test
    public void negative_not_change_reason_when_retro_remission_input_provided() {

        final String refundReference = performRefund(createPayment());
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
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

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

    }

    @Test
    public void positive_resubmit_refund_journey_when_contactDetails_amount_reason_provided() {

        final String refundReference = performRefund(createPayment());
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
        System.out.println("The value of the response status : " + responseReviewRefund.getStatusLine());
        System.out.println("The value of the response body : " + responseReviewRefund.getBody().asString());

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
        assertThat(optionalRefundDtoAfterUpdate.get().getAmount()).isEqualTo(new BigDecimal("80.00"));
        assertThat(optionalRefundDtoAfterUpdate.get().getContactDetails().getEmail()).isEqualTo("testperson@somemail.com");

    }
}
