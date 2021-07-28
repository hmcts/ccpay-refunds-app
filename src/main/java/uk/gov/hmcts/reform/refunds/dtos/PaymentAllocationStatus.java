package uk.gov.hmcts.reform.refunds.dtos;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAllocationStatus {

    private String name;

    private String description;
}
