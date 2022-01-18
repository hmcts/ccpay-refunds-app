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

    public static final RefundStatus ACCEPTED = new RefundStatus("Accepted", "Refund request accepted");
    public static final RefundStatus UPDATEREQUIRED = new RefundStatus("Update required", "Refund request update required");
    public static final RefundStatus REJECTED = new RefundStatus("Rejected", "Refund request rejected");
    public static final RefundStatus SENTFORAPPROVAL = new RefundStatus(
        "Sent for approval",
        "Refund request submitted"
    );
    public static final RefundStatus APPROVED = new RefundStatus(
        "Approved",
        "Refund request sent to liberata"
    );

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public static RefundStatus getRefundStatus(String name) {
        switch (name) {
            case "Sent for approval":
                return SENTFORAPPROVAL;
            case "Approved":
                return APPROVED;
            case "Update required":
                return UPDATEREQUIRED;
            case "Accepted":
                return ACCEPTED;
            case "Rejected":
                return REJECTED;
            default:
                throw new InvalidRefundRequestException("Invalid Refund status");
        }
    }

}
