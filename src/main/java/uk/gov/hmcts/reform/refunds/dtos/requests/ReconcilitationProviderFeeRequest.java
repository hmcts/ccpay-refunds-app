package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder(builderMethodName = "refundReconcilitationProviderFeeRequest")
public class ReconcilitationProviderFeeRequest {
    private String code;

    private String version;

    private BigDecimal refundAmount;
}
