package uk.gov.hmcts.reform.refunds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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
