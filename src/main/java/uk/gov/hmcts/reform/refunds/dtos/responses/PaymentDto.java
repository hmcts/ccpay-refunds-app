package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "payment2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuppressWarnings("PMD.TooManyFields")
public class PaymentDto {

    private String id;

    @NotEmpty
    private BigDecimal amount;

    @NotEmpty
    private String description;

    private String reference;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateUpdated;

    private CurrencyCode currency;

    private String ccdCaseNumber;

    private String caseReference;

    private String paymentReference;

    private String channel;

    private String method;

    private String externalProvider;

    private String status;

    private String externalReference;

    @NotEmpty
    private String siteId;

    private String serviceName;

    private String customerReference;

    private String accountNumber;

    private String organisationName;

    private String paymentGroupReference;

    private Date reportedDateOffline;

    private String documentControlNumber;

    private Date bankedDate;

    private String payerName;

    @NotNull
    private List<FeeDto> fees;

    private List<StatusHistoryDto> statusHistories;


    private String giroSlipNo;

}
