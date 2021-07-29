package uk.gov.hmcts.reform.refunds.model;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_status")
public class RefundStatus {


    public final static RefundStatus SUBMITTED = new RefundStatus("submitted", "submitted");
    public final static RefundStatus APPROVED = new RefundStatus("approved", "approved");
    public final static RefundStatus SENTBACK = new RefundStatus("sentback", "sentback");
    public final static RefundStatus ACCEPTED = new RefundStatus("accepted", "accepted");
    public final static RefundStatus REJECTED = new RefundStatus("rejected", "rejected");

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

}
