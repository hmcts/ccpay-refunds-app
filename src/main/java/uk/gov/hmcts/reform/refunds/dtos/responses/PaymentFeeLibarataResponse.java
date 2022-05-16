package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "feeLibarataDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentFeeLibarataResponse {
    private Integer id;

    private String code;

    private String version;

    private String naturalAccountCode;

    private String jurisdiction1;

    private String jurisdiction2;

    private String memoLine;

    private BigDecimal credit;

}
