package uk.gov.hmcts.reform.refunds.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_reasons")
@Builder(builderMethodName = "refundReasonWith")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RefundReason {
    @Id
    @Column(name = "code", nullable = false)
    String code;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description")
    String description;

    @Column(name = "recently_used")
    Boolean recentlyUsed;
}
