package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.refunds.model.RefundReason;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundLiberataRequestWith")
public class RefundLiberataRequest {

    private String refundReference;

    private String paymentReference;

    private Timestamp dateCreated;

    private Timestamp dateUpdated;

    private String refundReason;

    private BigDecimal totalRefundAmount;

    private String currency;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private List<RefundLiberataFee> fees;


}
