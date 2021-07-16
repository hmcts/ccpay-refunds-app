package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Builder(builderMethodName = "createRefundRequest")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonInclude(NON_NULL)
public class NewRefund {
    @JsonProperty("refunds_number")
    String refundsNumber;

    @JsonProperty("refunds_name")
    String refundsName;
}
