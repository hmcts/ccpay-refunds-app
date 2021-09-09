package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Builder(builderMethodName = "buildResubmitRefundResponseDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
public class ResubmitRefundResponseDto {

    private BigDecimal refundAmount;

    private String refundReference;
}
