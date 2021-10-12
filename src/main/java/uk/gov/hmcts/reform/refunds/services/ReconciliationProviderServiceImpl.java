package uk.gov.hmcts.reform.refunds.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;


@Service
public class ReconciliationProviderServiceImpl implements ReconciliationProviderService {

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationProviderServiceImpl.class);
    @Value("${reconciliation-provider.api.url}")
    private String reconciliationProviderApi;
    @Value("${reconciliation-provider.refund-status-update-path}")
    private String refundStatusUpdatePath;
    @Autowired
    private OAuth2RestOperations restTemplate;
    @Value("${liberata.api.key}")
    private String apiKey;

    @Override
    public ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(
        MultiValueMap<String, String> headers, ReconciliationProviderRequest reconciliationProviderRequest) {
        ReconciliationProviderRefundRequest reconciliationProviderRefundRequest = ReconciliationProviderRefundRequest
            .refundReconciliationProviderRefundRequestWith()
            .refundRequest(reconciliationProviderRequest).build();
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String json = ow.writeValueAsString(reconciliationProviderRefundRequest);
            LOG.info(json);
        } catch (JsonProcessingException e) {
            LOG.info(e.getMessage());
        }

        try {
            headers.add("X-API-KEY", apiKey);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reconciliationProviderApi + refundStatusUpdatePath);
            return restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(reconciliationProviderRefundRequest, headers), ReconciliationProviderResponse.class
            );
        } catch (HttpClientErrorException e) {
            LOG.error("HttpClientErrorException", e);
            throw new ReconciliationProviderInvalidRequestException("Invalid request. Please try again.", e);
        } catch (Exception e) {
            LOG.error("Reconciliation Provider", e);
            throw new ReconciliationProviderServerException(
                "Reconciliation provider unavailable. Please try again later.",
                e
            );
        }
    }

}
