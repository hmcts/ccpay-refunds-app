package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;


public interface ReconciliationProviderService {

    ResponseEntity<ReconciliationProviderResponse> updateReconciliationProviderWithApprovedRefund(
        ReconciliationProviderRequest reconciliationProviderRequest);

}
