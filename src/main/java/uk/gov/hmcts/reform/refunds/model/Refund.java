package uk.gov.hmcts.reform.refunds.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;

import java.sql.Timestamp;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Builder(builderMethodName = "refundsWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Data
@Table(name = "refunds")
@TypeDef(name = "json", typeClass = JsonType.class)
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

    @Column(name = "fee_ids")
    private String feeIds;

    @Column(name = "service_type")
    private String serviceType;

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

    @Column(name = "refund_approve_flag")
    private String refundApproveFlag;

    @Column(name = "notification_sent_flag")
    private String notificationSentFlag;

    @Type(type = "json")
    @Column(columnDefinition = "json", name = "contact_details")
    private ContactDetails contactDetails;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "refunds_id", referencedColumnName = "id", nullable = false)
    private List<StatusHistory> statusHistories;

    @Column(name = "refund_instruction_type")
    private String refundInstructionType;
}
