package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;

import java.math.BigDecimal;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundRequestWith")
public class RefundRequest {


    @NotNull(message = "Payment Reference cannot be null")
    @NotEmpty(message = "Payment Reference cannot be blank")
    private String paymentReference;

    @NotNull(message = "Refund Reason cannot be null")
    @NotEmpty(message = "Refund Reason cannot be blank")
    private String refundReason;

    @NotNull(message = "ccd_case_number cannot be null")
    @NotEmpty(message = "ccd_case_number cannot be blank")
    @Pattern(regexp = "^\\d{16}$", message = "ccd_case_number is not in valid format")
    private String ccdCaseNumber;

    @DecimalMin(value = "0.01", message = "Amount must be greater than or equal to 0.01")
    @Positive(message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Refund amount cannot have more than 2 decimal places")
    private BigDecimal refundAmount;

    @DecimalMin(value = "0.01", message = "Payment Amount must be greater than or equal to 0.01")
    @Positive(message = "Payment Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal paymentAmount;

    @NotNull(message = "fee_ids cannot be null")
    @NotEmpty(message = "fee_ids cannot be blank")
    @Pattern(regexp = "^([0-9]{1,10}(?:[\\.,]\\d{1,10}+)*+)$", message = "fee_ids is not in valid format")
    private String feeIds;

    @NotNull(message = "Contact Details cannot be null")
    private ContactDetails contactDetails;

    @NotNull(message = "Refund Fee  cannot be null")
    private List<RefundFeeDto> refundFees;

    @NotNull(message = "Service type cannot be null")
    @NotEmpty(message = "Service type cannot be blank")
    private String serviceType;

    private String paymentMethod;
    private String paymentChannel;
}
