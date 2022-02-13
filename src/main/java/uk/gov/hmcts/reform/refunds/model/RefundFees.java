
package uk.gov.hmcts.reform.refunds.model;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_fees")
@Builder(builderMethodName = "refundFeesWith")
@Inheritance(strategy = InheritanceType.JOINED)
public class RefundFees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunds_id", insertable = false, updatable = false)
    private Refund refund;

    @Column(name = "fee_id")
    private Integer feeId;

    @Column(name = "code")
    private String code;

    @Column(name = "version")
    private String version;

    @Column(name = "volume")
    private Integer volume;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

}

