package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class FeeDto {

    private Integer id;

    private String code;

    private String version;

    private Integer volume;

    private BigDecimal calculatedAmount;

    private BigDecimal feeAmount;

    private String memoLine;

    private String naturalAccountCode;

    private String ccdCaseNumber;

    private String reference;

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

    // The below 3 fields added as part of apportionment changes for Liberata

    private String paymentGroupReference;

    private BigDecimal apportionedPayment;

    private Date dateReceiptProcessed;

}
