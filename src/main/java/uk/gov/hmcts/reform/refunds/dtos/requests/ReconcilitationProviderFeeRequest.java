package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder(builderMethodName = "refundReconcilitationProviderFeeRequest")
public class ReconcilitationProviderFeeRequest {
    private String code;

    private int version;

    private String refundAmount;

    @Override
    public String toString() {
        return "ReconcilitationProviderFeeRequest{" +
                "code='" + code + '\'' +
                ", version=" + version +
                ", refundAmount='" + refundAmount + '\'' +
                '}';
    }
}
