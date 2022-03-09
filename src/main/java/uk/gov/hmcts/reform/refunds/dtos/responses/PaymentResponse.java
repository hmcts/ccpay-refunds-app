package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;


@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "paymentResponseWith")
@AllArgsConstructor
@Getter
@NoArgsConstructor
@ToString
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
