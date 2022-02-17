package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder(builderMethodName = "buildReconciliationProviderResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ReconciliationProviderResponse {

    private String refundReference;

    private BigDecimal amount;

    @Override
    public String toString() {
        return "ReconciliationProviderResponse{"
                + "refundReference='" + refundReference + '\''
                + ", amount=" + amount
                + '}';
    }
}
