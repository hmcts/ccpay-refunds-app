package uk.gov.hmcts.reform.refunds.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactFolder;
import feign.Feign;
import feign.Logger;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpHeaders;
import uk.gov.hmcts.reform.idam.client.IdamApi;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@ExtendWith(PactConsumerTestExt.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PactFolder("pacts")
public class UserManagementControllerConsumerTest {
    protected static final String SOME_AUTHORIZATION_TOKEN = "Bearer UserAuthToken";
    private static final String USERS_QUERY =
        "(roles:pui-case-manager) AND lastModified:>now-1h";

    @Pact(provider = "idamApi_users_management", consumer = "fp_refunds")
    public V4Pact generatePactForUserPage(PactDslWithProvider builder) {
        PactDslJsonArray responseBody = (PactDslJsonArray) new PactDslJsonArray()
            .object()
            .stringType("id", "1111-2222-3333-4567")
            .stringType("forename", "Case")
            .stringType("surname", "Officer")
            .stringType("email", "case.officer@example.com")
            .booleanType("active", true)
            .closeObject();

        return builder
            .given("access_token has been obtained as a user")
            .uponReceiving("a request for page of Users from FP Refunds API")
            .path("/api/v1/users")
            .method("GET")
            .query("query=" + USERS_QUERY)
            .matchHeader(HttpHeaders.AUTHORIZATION, SOME_AUTHORIZATION_TOKEN)
            .willRespondWith()
            .status(200)
            .body(responseBody)
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "generatePactForUserPage")
    void verifySearchUsers(MockServer mockServer) {
        IdamApi idam = Feign.builder()
            .contract(new SpringMvcContract())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .logLevel(Logger.Level.FULL)
            .target(IdamApi.class, mockServer.getUrl());

        List<?> users = idam.searchUsers(SOME_AUTHORIZATION_TOKEN, USERS_QUERY);

        assertNotNull(users);
        assertFalse(users.isEmpty());
    }
}
