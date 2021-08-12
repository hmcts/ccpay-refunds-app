package uk.gov.hmcts.reform.refunds.state;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RefundEvent {
    SUBMIT("Submit", "Refund request will be submitted"),
    REJECT("Reject", "There is no refund due"),
    SENDBACK("Return to caseworker", "Some information needs correction"),
    APPROVE("Approve", "Refund request will be approved"),
    ACCEPT("Accept", "Refund request will be accepted"),
    CANCEL("Cancel", "Refund request will be cancelled");

    private String code;
    private String label;

}
