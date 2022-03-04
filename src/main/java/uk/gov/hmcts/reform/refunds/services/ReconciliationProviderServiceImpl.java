package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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

    @Qualifier("restTemplateLiberata")
    @Autowired()
    private RestTemplate restTemplateLiberata;

    @Value("${liberata.api.key}")
    private String apiKey;

    @Override
    public ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(
        ReconciliationProviderRequest reconciliationProviderRequest) {
        ReconciliationProviderRefundRequest reconciliationProviderRefundRequest = ReconciliationProviderRefundRequest
            .refundReconciliationProviderRefundRequestWith()
            .refundRequest(reconciliationProviderRequest).build();
        try {
            MultiValueMap<String, String> header = new HttpHeaders();
            header.add("X-API-KEY", apiKey);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reconciliationProviderApi + refundStatusUpdatePath);
            return restTemplateLiberata.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(reconciliationProviderRefundRequest, header), ReconciliationProviderResponse.class
            );
        } catch (HttpClientErrorException e) {
            LOG.error("HttpClientErrorException Error", e);
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                throw new ReconciliationProviderInvalidRequestException("Duplicate request.", e);
            }
            throw new ReconciliationProviderInvalidRequestException("Invalid request. Please try again.", e);
        } catch (Exception e) {
            LOG.error("Reconciliation Provider", e);
            throw new ReconciliationProviderServerException(
                "Third-party reconciliation provider unavailable. Please try again later.",
                e
            );
        }
    }

}
