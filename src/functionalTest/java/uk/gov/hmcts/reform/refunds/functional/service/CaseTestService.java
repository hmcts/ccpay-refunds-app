package uk.gov.hmcts.reform.refunds.functional.service;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import jakarta.inject.Named;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Named
public class CaseTestService {

    public Response getPaymentGroupsForCase(final String userToken,
                                            final String serviceToken,
                                            final String ccdCaseNumber) {
        return givenWithAuthHeaders(userToken, serviceToken)
            .contentType(ContentType.JSON)
            .when()
            .get("/cases/{ccdcasenumber}/paymentgroups",ccdCaseNumber);
    }

    public RequestSpecification givenWithAuthHeaders(String userToken, String serviceToken) {
        return RestAssured.given()
            .header(AUTHORIZATION, userToken)
            .header("ServiceAuthorization", serviceToken);
    }
}
