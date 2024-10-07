package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@With
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundResubmitRequestPayhubWith")
@Setter
@Getter
public class RefundResubmitPayhubRequest {

    @NotNull
    private String refundReason;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    private String feeId;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Total Refunded amount cannot have more than 2 decimal places")
    private BigDecimal totalRefundedAmount;
}
