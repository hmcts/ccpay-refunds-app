package uk.gov.hmcts.reform.refunds.services;

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
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;

@Service
public class ReconciliationProviderServiceImpl implements ReconciliationProviderService{

    @Value("${reconciliation-provider.api.url}")
    private String reconciliationProviderApi;

    @Value("${reconciliation-provider.refund-status-update-path}")
    private String refundStatusUpdatePath;

    @Autowired
    private OAuth2RestOperations restTemplate;


    @Override
    public ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(MultiValueMap<String, String> headers, ReconciliationProviderRequest reconciliationProviderRequest){
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reconciliationProviderApi + refundStatusUpdatePath);
            return restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(reconciliationProviderRequest, headers), ReconciliationProviderResponse.class
            );
        } catch (HttpClientErrorException e){
            throw new ReconciliationProviderInvalidRequestException("Invalid Request: Reconciliation Provider", e);
        } catch ( Exception e){
            throw new ReconciliationProviderServerException("Reconciliation Provider Server Exception", e);
        }
    }

}
