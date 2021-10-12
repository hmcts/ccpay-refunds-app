package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.math.BigDecimal;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

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
}
