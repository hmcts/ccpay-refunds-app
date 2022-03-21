package uk.gov.hmcts.reform.refunds.functional.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundRequestWith")
public class PaymentRefundRequest {

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

    @Digits(integer = 10, fraction = 2, message = "Please check the amount you want to refund")
    @NotNull(message = "You need to enter a refund amount")
    private BigDecimal refundAmount;

    @NotEmpty
    @Valid
    private List<FeeDto> fees;

    @NotNull(message = "Contact Details cannot be null")
    private ContactDetails contactDetails;

    @NotNull(message = "Service type cannot be null")
    @NotEmpty(message = "Service type cannot be blank")
    private String serviceType;

    private String paymentMethod;
    private String paymentChannel;


}
