package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundReconciliationProviderRequestWith")
public class ReconciliationProviderRequest {

    private String refundReference;

    private String paymentReference;

    private Date dateCreated;

    private Date dateUpdated;

    private String refundReason;

    private BigDecimal totalRefundAmount;

    private String currency;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private List<ReconcilitationProviderFeeRequest> fees;


}
