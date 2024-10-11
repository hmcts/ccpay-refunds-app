package uk.gov.hmcts.reform.refunds.functional.response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder(builderMethodName = "paymentAllocationStatusWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_allocation_status")
public class PaymentAllocationStatus {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
