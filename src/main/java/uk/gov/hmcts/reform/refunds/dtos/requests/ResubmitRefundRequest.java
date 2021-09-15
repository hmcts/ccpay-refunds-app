package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.validation.constraints.AssertTrue;
import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "ResubmitRefundRequestWith")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ResubmitRefundRequest {

    private String refundReason;

    private BigDecimal amount;

    @AssertTrue(message = "Refund amount should not be null")
    private boolean isRequestEmpty() {
        return amount != null || !"".equals(amount);
    }

}
