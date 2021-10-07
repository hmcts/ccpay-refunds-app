package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "refundReconciliationProviderRequestWith")
public class ReconciliationProviderRequest {

    private String refundReference;

    private String paymentReference;

    private String dateCreated;

    private String dateUpdated;

    private String refundReason;

    private double totalRefundAmount;

    private String currency;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private List<ReconcilitationProviderFeeRequest> fees;


}
