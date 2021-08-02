package uk.gov.hmcts.reform.refunds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "status_history")
@Builder(builderMethodName = "statusHistoryWith")
@Inheritance(strategy = InheritanceType.JOINED)
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunds_id", insertable = false, updatable = false)
    private Refund refund;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "date_created")
    private Timestamp dateCreated;

    @Column(name = "status")
    private String status;

    //rejection reasons from user request
    @Column(name = "notes")
    private String notes;

}
