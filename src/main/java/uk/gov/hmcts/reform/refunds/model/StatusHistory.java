package uk.gov.hmcts.reform.refunds.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "status_history")
public class StatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", insertable = false, updatable = false)
    private Refund refund;

    @Column(name = "reason")
    private String reason;

    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "reference")
    private String reference;

    @Column(name = "payment_reference")
    private String paymentReference;
}
