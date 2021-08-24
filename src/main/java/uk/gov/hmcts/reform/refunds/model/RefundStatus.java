package uk.gov.hmcts.reform.refunds.model;

import lombok.*;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;

import javax.persistence.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(builderMethodName = "refundStatusWith")
@Table(name = "refund_status")
public class RefundStatus {


    public final static RefundStatus SENTFORAPPROVAL = new RefundStatus(
        "sent for approval",
        "Refund request submitted"
    );
    public final static RefundStatus SENTTOMIDDLEOFFICE = new RefundStatus(
        "sent to middle office",
        "Refund request sent to liberata"
    );
    public final static RefundStatus SENTBACK = new RefundStatus("sent back", "Refund request sent back");
    public final static RefundStatus ACCEPTED = new RefundStatus("accepted", "Refund request accepted");
    public final static RefundStatus REJECTED = new RefundStatus("rejected", "Refund request rejected");

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public static RefundStatus getRefundStatus(String name) {
        switch (name) {
            case "sent for approval":
                return SENTFORAPPROVAL;
            case "sent to middle office":
                return SENTTOMIDDLEOFFICE;
            case "sent back":
                return SENTBACK;
            case "accepted":
                return ACCEPTED;
            case "rejected":
                return REJECTED;
            default:
                throw new InvalidRefundRequestException("Invalid Refund status");
        }
    }

}
