package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "paymentGroupDtoWith")
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class PaymentGroupResponse {

    private String paymentGroupReference;

    private Date dateCreated;

    private Date dateUpdated;

    private List<PaymentResponse> payments;

    private List<RemissionResponse> remissions;

    @Valid
    private List<PaymentFeeResponse> fees;

}
