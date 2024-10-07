package uk.gov.hmcts.reform.refunds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "status_history")
@Builder(builderMethodName = "statusHistoryWith")
@Inheritance(strategy = InheritanceType.JOINED)
@ToString
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ToString.Exclude
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
