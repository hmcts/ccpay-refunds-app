package uk.gov.hmcts.reform.refunds.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Builder(builderMethodName = "refundReasonWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_reasons")
public class RefundReason {
    @Id
    @Column(name = "code", nullable = false)
    String code;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description")
    String description;
}
