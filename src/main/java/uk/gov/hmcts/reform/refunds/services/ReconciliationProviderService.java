package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;


public interface ReconciliationProviderService {

    ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(MultiValueMap<String, String> headers, ReconciliationProviderRequest reconciliationProviderRequest);

}
