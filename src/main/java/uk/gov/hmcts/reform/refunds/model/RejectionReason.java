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
@Builder(builderMethodName = "rejectionReasonWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "rejection_reasons")
public class RejectionReason {
    @Id
    @Column(name = "code", nullable = false)
    String code;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description")
    String description;
}
