package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Entity
@Builder(builderMethodName = "refundsWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refunds")
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "amount")
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "reason")
    private Reason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_status")
    private RefundStatus refundStatus;

    @Column(name = "reference")
    private String reference;

    @Column(name = "payment_reference")
    private String paymentReference;

    @UpdateTimestamp
    @Column(name = "date_created")
    private Timestamp dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Timestamp dateUpdated;


    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "refund_id", referencedColumnName = "id", nullable = false)
    private List<StatusHistory> statusHistories;

}
