package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "refundsReportDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefundsReportDto {

    @JsonProperty("date_created")
    private Date refundDateCreated;

    @JsonProperty("date_updated")
    private Date refundDateUpdated;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("reference [RF Number]")
    private String refundReference;

    @JsonProperty("payment_reference")
    private String paymentReference;

    @JsonProperty("ccd_case_number")
    private String ccdCaseNumber;

    @JsonProperty("service_type")
    private String serviceType;

    @JsonProperty("refund_status")
    private String refundStatus;

    @JsonProperty("refund_status_reason")
    private String notes;
}
