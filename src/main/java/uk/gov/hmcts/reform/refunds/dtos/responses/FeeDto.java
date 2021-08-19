package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "feeDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
@EqualsAndHashCode
@SuppressWarnings("PMD.TooManyFields")
public class FeeDto {

    private Integer id;

    @NotEmpty
    private String code;

    @NotEmpty
    private String version;

    @Positive
    private Integer volume;

    @NotNull
    @Digits(integer = 10, fraction = 2, message = "Fee calculated amount cannot have more than 2 decimal places")
    private BigDecimal calculatedAmount;

    private BigDecimal feeAmount;

    private String memoLine;

    private String naturalAccountCode;

    private String ccdCaseNumber;

    private String reference;

    @Digits(integer = 10, fraction = 2, message = "Net amount cannot have more than 2 decimal places")
    private BigDecimal netAmount;

    private String jurisdiction1;

    private String jurisdiction2;

    private String description;

    private String caseReference;

    private BigDecimal apportionAmount;

    private BigDecimal allocatedAmount;

    private Date dateApportioned;

    private Date dateCreated;

    private Date dateUpdated;

    private BigDecimal amountDue;

    private String paymentGroupReference;

    private BigDecimal apportionedPayment;

    private Date dateReceiptProcessed;

}
