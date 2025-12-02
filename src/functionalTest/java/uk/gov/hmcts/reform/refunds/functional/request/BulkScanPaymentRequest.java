package uk.gov.hmcts.reform.refunds.functional.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import uk.gov.hmcts.reform.refunds.functional.util.PaymentMethodType;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@With
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createBulkScanPaymentWith")
public class BulkScanPaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotNull
    private PaymentMethodType paymentMethod;

    @NotNull
    @JsonProperty("requestor")
    private String service;

    private String ccdCaseNumber;

    private String exceptionRecord;

    @NotNull
    private PaymentChannel paymentChannel;

    @NotNull
    private PaymentStatus paymentStatus;

    @NotNull
    private String currency;

    @JsonProperty("external_provider")
    private String paymentProvider;

    @NotNull
    private String giroSlipNo;

    @NotNull
    private String bankedDate;

    @NotEmpty
    @JsonProperty("site_id")
    private String siteId;

    private String payerName;

    @NotEmpty
    @NotNull
    private String documentControlNumber;

}


