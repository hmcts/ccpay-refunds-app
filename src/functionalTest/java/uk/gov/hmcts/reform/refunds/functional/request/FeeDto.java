package uk.gov.hmcts.reform.refunds.functional.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "feeDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FeeDto {

    private Integer id;

    @NotEmpty
    private String code;

    @NotEmpty
    private String version;

    @Positive
    private Integer updatedVolume;

    @NotNull
    @Digits(integer = 10, fraction = 2, message = "Fee calculated amount cannot have more than 2 decimal places")
    private BigDecimal calculatedAmount;

    private BigDecimal apportionAmount;

    private BigDecimal refundAmount;
}
