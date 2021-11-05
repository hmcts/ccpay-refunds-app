package uk.gov.hmcts.reform.refunds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(builderMethodName = "refundStatusWith")
@Table(name = "refund_status")
public class RefundStatus {

    public static final RefundStatus ACCEPTED = new RefundStatus("accepted", "Refund request accepted");
    public static final RefundStatus UPDATEREQUIRED = new RefundStatus("update required", "Refund request update required");
    public static final RefundStatus REJECTED = new RefundStatus("rejected", "Refund request rejected");
    public static final RefundStatus SENTFORAPPROVAL = new RefundStatus(
        "sent for approval",
        "Refund request submitted"
    );
    public static final RefundStatus APPROVED = new RefundStatus(
        "approved",
        "Refund request sent to liberata"
    );

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public static RefundStatus getRefundStatus(String name) {
        switch (name) {
            case "sent for approval":
                return SENTFORAPPROVAL;
            case "approved":
                return APPROVED;
            case "update required":
                return UPDATEREQUIRED;
            case "accepted":
                return ACCEPTED;
            case "rejected":
                return REJECTED;
            default:
                throw new InvalidRefundRequestException("Invalid Refund status");
        }
    }

}
