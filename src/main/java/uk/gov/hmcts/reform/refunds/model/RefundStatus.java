package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import javax.persistence.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(builderMethodName = "refundStatusWith")
@Table(name = "refund_status")
public class RefundStatus {


    public final static RefundStatus SENTFORAPPROVAL = new RefundStatus("sent for approval", "Refund request submitted");
    public final static RefundStatus SENTTOMIDDLEOFFICE = new RefundStatus("sent to middle office", "Refund request sent to liberata");
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
           case "sent for approval":
               return RefundState.SENTFORAPPROVAL;
           case "sent to middle office":
               return RefundState.SENTTOMIDDLEOFFICE;
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
