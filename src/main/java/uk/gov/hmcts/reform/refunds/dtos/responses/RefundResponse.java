package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder(builderMethodName = "buildRefundResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefundResponse {

    private String refundReference;
}
