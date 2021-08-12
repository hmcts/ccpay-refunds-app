package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;

import java.util.List;


@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "paymentFeeDetailsWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentFeeDetails {

    private String reference;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private List<FeeDto> fees;
}
