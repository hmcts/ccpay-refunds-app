package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "buildRefundLibarataWith")
@AllArgsConstructor
@Getter
@NoArgsConstructor

public class RefundLibarata {


    private String refundReference;
    private String refundReason;
    private String refundInstructionType;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date refundDateApproved;
    private BigDecimal totalRefundAmount;
    private List<PaymentFeeLibarataResponse> fees;

    private PaymentRefundDto payment;

}
