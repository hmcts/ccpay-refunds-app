package uk.gov.hmcts.reform.refunds.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetailsResponse;
import uk.gov.hmcts.reform.refunds.model.IacSupplementaryRequest;
import uk.gov.hmcts.reform.refunds.support.LocalHttpStubServer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/*
 * Sends real loopback HTTP through the production RestTemplate and HttpClient beans: JSON request
 * and response conversion, auth headers, PATCH, 4xx/5xx handling, and the token parsers' client.
 * Protects against broken outbound calls to IDAM, Payment, Notify and IAC, dropped auth headers,
 * and upstream errors being silently accepted. Service tests mock RestTemplate, so they cannot
 * see failures in the transport or its message converters.
 */
class RestTemplateConfigurationTest {

    private static final String PATH = "/cases/supplementary-info";
    private final RestTemplateConfiguration configuration = new RestTemplateConfiguration();
    private LocalHttpStubServer server;
    private RestTemplate client;

    @BeforeEach
    void startServer() throws Exception {
        server = new LocalHttpStubServer();
    }

    @AfterEach
    void closeResources() throws Exception {
        if (client != null) {
            destroy(client);
        }
        server.close();
    }

    @Test
    void allRestTemplatesUseApacheHttpComponents() throws Exception {
        for (RestTemplate restTemplate : List.of(configuration.restTemplateIdam(), configuration.restTemplatePayment(),
            configuration.restTemplateNotify(), configuration.restTemplateIacSupplementaryInfo())) {
            assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
            destroy(restTemplate);
        }
    }

    @Test
    void iacClientSendsJsonAndAuthHeadersAndReadsSupplementaryResponse() throws Exception {
        client = configuration.restTemplateIacSupplementaryInfo();
        server.respond(PATH, 200, "{\"supplementary_info\":[{\"ccd_case_number\":\"1111222233334444\","
            + "\"supplementary_details\":{\"surname\":\"O'Neill–Jones\",\"case_reference_number\":\"IAC/1234\"}}],"
            + "\"missing_supplementary_info\":{\"ccd_case_numbers\":[]}}");
        IacSupplementaryRequest body = new IacSupplementaryRequest(List.of("1111222233334444"));

        ResponseEntity<SupplementaryDetailsResponse> response = client.exchange(server.uri(PATH), HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()), SupplementaryDetailsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var details = response.getBody().getSupplementaryInfo().getFirst().getSupplementaryDetails();
        assertThat(details.getSurname()).isEqualTo("O'Neill–Jones");
        assertThat(details.getCaseReferenceNumber()).isEqualTo("IAC/1234");
        assertThat(response.getBody().getMissingSupplementaryInfo().getCcdCaseNumbers()).isEmpty();
        LocalHttpStubServer.Request request = server.nextRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer user-token");
        assertThat(request.headers().getFirst("ServiceAuthorization")).isEqualTo("service-token");
        assertThat(new ObjectMapper().readTree(request.body()))
            .isEqualTo(new ObjectMapper().readTree("{\"ccd_case_numbers\":[\"1111222233334444\"]}"));
    }

    @Test
    void paymentClientSupportsPatch() throws Exception {
        client = configuration.restTemplatePayment();
        server.respond(PATH, 200, "{\"status\":\"updated\"}");

        ResponseEntity<String> response = client.exchange(server.uri(PATH), HttpMethod.PATCH,
            new HttpEntity<>(new IacSupplementaryRequest(List.of("1234")), authHeaders()), String.class);

        assertThat(response.getBody()).isEqualTo("{\"status\":\"updated\"}");
        LocalHttpStubServer.Request request = server.nextRequest();
        assertThat(request.method()).isEqualTo("PATCH");
        assertThat(request.body()).isEqualTo("{\"ccd_case_numbers\":[\"1234\"]}");
    }

    @Test
    void preservesClientErrorStatusAndBody() {
        client = configuration.restTemplatePayment();
        server.respond(PATH, 404, "{\"message\":\"Case not found\"}");

        assertThatExceptionOfType(HttpClientErrorException.class)
            .isThrownBy(() -> client.getForObject(server.uri(PATH), String.class))
            .satisfies(error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(error.getResponseBodyAsString()).isEqualTo("{\"message\":\"Case not found\"}");
            });
    }

    @Test
    void preservesServerErrorStatusAndBody() {
        client = configuration.restTemplatePayment();
        server.respond(PATH, 503, "{\"message\":\"Service unavailable\"}");

        assertThatExceptionOfType(HttpServerErrorException.class)
            .isThrownBy(() -> client.getForObject(server.uri(PATH), String.class))
            .satisfies(error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.getResponseBodyAsString()).isEqualTo("{\"message\":\"Service unavailable\"}");
            });
    }

    @Test
    void tokenParserHttpClientPerformsRequests() throws Exception {
        server.respond("/details", 200, "{\"id\":\"user-123\"}");

        try (CloseableHttpClient httpClient = configuration.paymentsHttpClient();
             CloseableHttpResponse response = httpClient.execute(new HttpGet(server.uri("/details")))) {
            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            assertThat(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":\"user-123\"}");
        }
        assertThat(server.nextRequest().method()).isEqualTo("GET");
    }

    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("user-token");
        headers.set("ServiceAuthorization", "service-token");
        return headers;
    }

    private static void destroy(RestTemplate restTemplate) throws Exception {
        ((HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory()).destroy();
    }
}
