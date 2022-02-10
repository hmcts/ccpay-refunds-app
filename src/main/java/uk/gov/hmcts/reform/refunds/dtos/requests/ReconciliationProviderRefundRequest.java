package uk.gov.hmcts.reform.refunds.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundReconciliationProviderRefundRequestWith")
public class ReconciliationProviderRefundRequest {
    private ReconciliationProviderRequest refundRequest;

    @Override
    public String toString() {
        return "ReconciliationProviderRefundRequest{"
                + "refundRequest=" + refundRequest
                + '}';
    }
}
