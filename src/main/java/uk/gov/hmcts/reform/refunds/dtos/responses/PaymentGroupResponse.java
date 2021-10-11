package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

import javax.validation.Valid;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "paymentGroupDtoWith")
@AllArgsConstructor
@Getter
@Setter
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
