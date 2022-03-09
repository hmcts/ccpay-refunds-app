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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "feeDtoWith")
@AllArgsConstructor
@Getter
@NoArgsConstructor
@ToString
public class PaymentFeeResponse {

    private BigDecimal calculatedAmount;

    private String code;

    private BigDecimal netAmount;

    private String version;

    private Integer volume;

    private BigDecimal feeAmount;

    private String ccdCaseNumber;

    private String reference;

    private Integer id;

    private String memoLine;

    private String naturalAccountCode;

    private String description;

    private BigDecimal allocatedAmount;

    private BigDecimal apportionAmount;

    private Date dateCreated;

    private Date dateUpdated;

    private Date dateApportioned;

    private BigDecimal amountDue;

}
