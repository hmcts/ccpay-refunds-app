package uk.gov.hmcts.reform.refunds.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactFolder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import feign.Feign;
import feign.Logger;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

@ExtendWith(PactConsumerTestExt.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PactFolder("pacts")
public class OidcControllerConsumerTest {
    protected static final String SOME_AUTHORIZATION_TOKEN = "Bearer UserAuthToken";

    @Pact(provider = "idamApi_oidc", consumer = "fp_refunds")
    public V4Pact generatePactForUserInfo(PactDslWithProvider builder) {
        PactDslJsonBody body = new PactDslJsonBody()
            .stringType("sub", "ia-caseofficer@fake.hmcts.net")
            .stringType("uid", "1111-2222-3333-4567")
            .stringType("givenName", "Case")
            .stringType("familyName", "Officer")
            .stringType("IDAM_ADMIN_USER", "idamAdminUser")
            .minArrayLike("roles", 1,
                PactDslJsonRootValue.stringType("caseworker-ia-legalrep-solicitor"), 1);

        return builder
            .given("userinfo is requested")
            .uponReceiving("a request for UserInfo from FP Refunds API")
            .path("/o/userinfo")
            .method("GET")
            .matchHeader(HttpHeaders.AUTHORIZATION, SOME_AUTHORIZATION_TOKEN)
            .willRespondWith()
            .status(200)
            .body(body)
            .toPact(V4Pact.class);
    }

    @Pact(provider = "idamApi_oidc", consumer = "fp_refunds")
    public V4Pact generatePactForToken(PactDslWithProvider builder) {
        Map<String, String> responseheaders = ImmutableMap.<String, String>builder()
            .put("Content-Type", "application/json")
            .build();

        return builder
            .given("a token is requested")
            .uponReceiving("Provider receives a POST /o/token request from FP Refunds API")
            .path("/o/token")
            .method("POST")
            .headers("Content-Type", APPLICATION_FORM_URLENCODED_VALUE)
            .body(
                "redirect_uri=http%3A%2F%2Fwww.dummy-pact-service.com%2Fcallback"
                + "&client_id=pact"
                + "&grant_type=password"
                + "&username=caseworkerUsername"
                + "&password=caseworkerPwd"
                + "&client_secret=clientSecret"
                + "&scope=openid%20profile%20roles"
            )
            .willRespondWith()
            .status(HttpStatus.OK.value())
            .headers(responseheaders)
            .body(createAuthResponse())
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "generatePactForUserInfo")
    public void verifyIdamUserDetailsRolesPactUserInfo(MockServer mockServer) {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        IdamApi idam = Feign.builder()
            .contract(new SpringMvcContract())
            .encoder(new JacksonEncoder(mapper))
            .decoder(new JacksonDecoder(mapper))
            .logLevel(Logger.Level.FULL)
            .target(IdamApi.class, mockServer.getUrl());

        UserInfo userInfo = idam.retrieveUserInfo(SOME_AUTHORIZATION_TOKEN);

        assertNotNull(userInfo);
    }

    @Test
    @PactTestFor(pactMethod = "generatePactForToken")
    public void verifyIdamUserDetailsRolesPactToken(MockServer mockServer) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("redirect_uri", "http://www.dummy-pact-service.com/callback");
        form.add("client_id", "pact");
        form.add("grant_type", "password");
        form.add("username", "caseworkerUsername");
        form.add("password", "caseworkerPwd");
        form.add("client_secret", "clientSecret");
        form.add("scope", "openid profile roles");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(
            mockServer.getUrl() + "/o/token",
            request,
            String.class
        );

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());

        JsonNode json = new ObjectMapper().readTree(response.getBody());
        assertTrue(json.hasNonNull("access_token"), "access_token is expected");
        assertFalse(json.get("access_token").asText().isBlank());
        assertEquals("openid roles profile", json.get("scope").asText());
    }

    private PactDslJsonBody createAuthResponse() {
        return new PactDslJsonBody()
            .stringType("access_token", "eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwia2lkI")
            .stringType("scope", "openid roles profile");
    }
}
