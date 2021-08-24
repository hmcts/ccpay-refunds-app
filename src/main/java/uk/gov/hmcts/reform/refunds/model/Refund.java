package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Entity
@Builder(builderMethodName = "refundsWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Data
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ccd_case_number")
    private String ccdCaseNumber;

    @Column(name = "amount")
    private BigDecimal amount;

    @JoinColumn(name = "reason")
    private String reason;

    @ManyToOne
    @JoinColumn(name = "refund_status")
    private RefundStatus refundStatus;

    @Column(name = "reference")
    private String reference;

    @Column(name = "payment_reference")
    private String paymentReference;

    @CreationTimestamp
    @Column(name = "date_created")
    private Timestamp dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Timestamp dateUpdated;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "refunds_id", referencedColumnName = "id", nullable = false)
    private List<StatusHistory> statusHistories;


}
