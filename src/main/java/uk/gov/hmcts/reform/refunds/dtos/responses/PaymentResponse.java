package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


@JsonNaming(SnakeCaseStrategy.class)
@Builder(builderMethodName = "paymentResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentResponse {

    private String reference;

    private BigDecimal amount;

    private CurrencyCode currency;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private String organisationName;

    private String customerReference;

    private String status;

    private String serviceName;

    private String siteId;

    private String description;

    private String channel;

    private String method;

    private String externalReference;

    private String externalProvider;

    private Date dateCreated;

    private Date dateUpdated;

    private String documentControlNumber;

    private Date bankedDate;

    private String payerName;

    private List<PaymentAllocationResponse> paymentAllocation;

}
