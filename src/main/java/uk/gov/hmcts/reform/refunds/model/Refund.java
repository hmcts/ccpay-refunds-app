package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Builder(builderMethodName = "refundsWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "refunds")
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "refunds_id")
    private String refundsId;

    @Column(name = "date_created")
    private Timestamp dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Timestamp dateUpdated;

}
