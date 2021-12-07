package uk.gov.hmcts.reform.refunds.state;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RefundEvent {

    SUBMIT("Submit", "Send for approval"),
    REJECT("Reject", "There is no refund due"),
    SENDBACK("Return to caseworker", "Some information needs correcting"),
    APPROVE("Approve", "Send to middle office"),
    ACCEPT("Accept", "Refund request accepted"),
    CANCEL("Cancel", "Refund request cancelled");

    private String code;
    private String label;

}
