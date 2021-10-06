package uk.gov.hmcts.reform.refunds.services;

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
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;


@Service
public class ReconciliationProviderServiceImpl implements ReconciliationProviderService{

    @Value("${reconciliation-provider.api.url}")
    private String reconciliationProviderApi;

    @Value("${reconciliation-provider.refund-status-update-path}")
    private String refundStatusUpdatePath;

    @Autowired
    private OAuth2RestOperations restTemplate;

    @Value("${liberata.api.key}")
    private String xApikey;

    @Value("${liberata.oauth2.username}")
    private String username;

    @Value("${liberata.oauth2.password}")
    private String password;

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationProviderServiceImpl.class);

    @Override
    public ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(MultiValueMap<String, String> headers, ReconciliationProviderRequest reconciliationProviderRequest){
//        try{
            headers.add("X-API-KEY",xApikey);
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reconciliationProviderApi + refundStatusUpdatePath);
            return restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(reconciliationProviderRequest, headers), ReconciliationProviderResponse.class
            );
//        } catch (HttpClientErrorException e){
//            throw new ReconciliationProviderInvalidRequestException("Invalid Request: Reconciliation Provider", e);
//        } catch ( Exception e){
//            throw new ReconciliationProviderServerException("Reconciliation Provider Server Exception", e);
//        }
    }

}
