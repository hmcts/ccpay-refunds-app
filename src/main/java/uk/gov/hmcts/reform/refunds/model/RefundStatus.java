package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import javax.persistence.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_status")
public class RefundStatus {


    public final static RefundStatus SUBMITTED = new RefundStatus("submitted", "Refund request submitted");
    public final static RefundStatus SENT_TO_LIBERATA = new RefundStatus("sent to liberata", "Refund request sent to liberata");
    public final static RefundStatus SENTBACK = new RefundStatus("sent back", "Refund request sent back");
    public final static RefundStatus ACCEPTED = new RefundStatus("accepted", "Refund request accepted");
    public final static RefundStatus REJECTED = new RefundStatus("rejected", "Refund request rejected");

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public RefundState getRefundState(){
       switch (this.name){
           case "submitted":
               return RefundState.SUBMITTED;
           case "sent to liberata":
               return RefundState.SENT_TO_LIBERATA;
           case "sent back":
               return RefundState.NEEDMOREINFO;
           case "accepted":
               return RefundState.ACCEPTED;
           case "rejected":
               return RefundState.REJECTED;
           default:
               throw new InvalidRefundRequestException("Invalid State");
       }
    }

}
