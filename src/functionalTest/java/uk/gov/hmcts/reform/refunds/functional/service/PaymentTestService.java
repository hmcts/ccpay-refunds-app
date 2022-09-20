package uk.gov.hmcts.reform.refunds.functional.service;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.serenitybdd.rest.SerenityRest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;

import javax.inject.Named;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Named
public class PaymentTestService {

    public Response postPbaPayment(final String userToken,
                                   final String serviceToken,
                                   final String baseUri,
                                   final CreditAccountPaymentRequest request) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON)
            .baseUri(baseUri)
            .body(request)
            .when()
            .post("/credit-account-payments");
    }

    public Response getPbaPaymentsByAccountNumber(final String userToken,
                                                  final String serviceToken,
                                                  final String accountNumber,
                                                  final String baseUri) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .when()
            .baseUri(baseUri)
            .get("/pba-accounts/{accountNumber}/payments", accountNumber);
    }

    public Response postInitiateRefund(final String userToken, final String serviceToken,
                                       final PaymentRefundRequest paymentRefundRequest,
                                       final String baseUri) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON)
            .baseUri(baseUri)
            .body(paymentRefundRequest)
            .when()
            .post("/refund-for-payment");
    }

    public Response getRetrieveActions(final String userToken, final String serviceToken, final String reference) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when().get("/refund/{reference}/actions", reference);
    }

    public Response patchReviewRefund(final String userToken, final String serviceToken, final String reference,
                                      final String action, RefundReviewRequest refundReviewRequest) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .body(refundReviewRequest)
            .patch("/refund/{reference}/action/{reviewer-action}", reference, action);
    }

    public Response getRefundList(final String userToken,
                                  final String serviceToken,
                                  final String status,
                                  final String excludeCurrentUser) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .queryParams("status", status)
            .queryParam("excludeCurrentUser", excludeCurrentUser)
            .get("/refund");
    }

    public Response getRefundList(final String userToken,
                                  final String serviceToken,
                                  final String ccdCaseNumber) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .queryParams("ccdCaseNumber", ccdCaseNumber)
            .get("/refund");
    }

    public Response getStatusHistory(final String userToken,
                                     final String serviceToken,
                                     final String refundReference) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .get("/refund/{reference}/status-history", refundReference);
    }

    public Response getRefundReasons(final String userToken, final String serviceToken) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when().get("/refund/reasons");
    }

    public Response updateRefundStatus(final String userToken,
                                       final String serviceToken,
                                       final String refundReference,
                                       final RefundStatusUpdateRequest refundStatusUpdateRequest) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .body(refundStatusUpdateRequest)
            .patch("/refund/{reference}", refundReference);
    }

    public Response resubmitRefund(final String userToken, final String serviceToken,
                                   final ResubmitRefundRequest resubmitRefundRequest,
                                   final String refundReference) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON).when()
            .body(resubmitRefundRequest)
            .patch("/refund/resubmit/{reference}", refundReference);
    }

    public RequestSpecification givenWithAuthHeaders(final String userToken, final String serviceToken) {
        return SerenityRest.given()
            .header(AUTHORIZATION, userToken)
            .header("ServiceAuthorization", serviceToken);
    }

    public Response getPbaPayment(String userToken, String serviceToken, String paymentReference, final String baseUri) {
        return givenWithAuthHeaders(userToken, serviceToken)
                .baseUri(baseUri)
                .when()
                .get("/credit-account-payments/{reference}", paymentReference);
    }

    public Response deletePayment(String userToken, String serviceToken, String paymentReference, final String baseUri) {
        return givenWithAuthHeaders(userToken, serviceToken)
                .baseUri(baseUri)
                .when()
                .delete("/credit-account-payments/{paymentReference}", paymentReference);
    }

    public Response deleteRefund(final String userToken, final String serviceToken,
                                 final String refundReference) {
        return givenWithAuthHeaders(userToken, serviceToken)
                .delete("/refund/{reference}", refundReference);
    }

    public Response patchCancelRefunds(final String serviceToken, final String paymentReference) {
        return givenWithServiceHeaders(serviceToken)
            .contentType(ContentType.JSON).when()
            .patch("/payment/{paymentReference}/action/cancel", paymentReference);
    }

    public RequestSpecification givenWithServiceHeaders(String serviceToken) {
        return RestAssured.given()
            .header("ServiceAuthorization", serviceToken);
    }
}
