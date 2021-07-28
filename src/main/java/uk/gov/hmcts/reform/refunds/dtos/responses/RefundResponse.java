package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder(builderMethodName = "buildRefundResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class RefundResponse {

    private String refundReference;
}
