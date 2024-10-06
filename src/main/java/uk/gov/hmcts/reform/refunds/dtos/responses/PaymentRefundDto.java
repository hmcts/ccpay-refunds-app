package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@JsonNaming(SnakeCaseStrategy.class)
@Builder(builderMethodName = "paymentRefundDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentRefundDto {

    private String reference;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateReceiptCreated;
    private String serviceName;
    private String siteId;
    private String channel;
    private String method;
    private String ccdCaseNumber;
    private String caseReference;
    private String customerReference;
    private String pbaNumber;
    private String govUkId;
    private String bgcNumber;
    private BigDecimal availableFunds;

}
