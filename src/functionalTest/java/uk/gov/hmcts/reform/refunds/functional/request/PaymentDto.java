package uk.gov.hmcts.reform.refunds.functional.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "payment2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
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

    private String currency;

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

    private Boolean refundEnable;

    private boolean issueRefundAddRefundAddRemission;

    private boolean issueRefund;

    //@JsonUnwrapped
    @NotNull
    private List<FeeDto> fees;

    private String giroSlipNo;

    private String internalReference;

    private BigDecimal overPayment;

    @JsonProperty("_links")
    private LinksDto links;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    public static class LinksDto {
        private LinkDto nextUrl;
        private LinkDto self;
        private LinkDto cancel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(NON_NULL)
    public static class LinkDto {
        private String href;
        private String method;
    }
}
