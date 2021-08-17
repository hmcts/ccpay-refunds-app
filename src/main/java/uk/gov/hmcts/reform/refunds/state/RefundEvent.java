package uk.gov.hmcts.reform.refunds.state;

public enum RefundEvent {

    SUBMIT("Submit", "Send for approval"),
    REJECT("Reject", "There is no refund due"),
    SENDBACK("Return to caseworker", "Some information needs correction"),
    APPROVE("Approve", "Send to middle office"),
    ACCEPT("Accept", "Refund request accepted"),
    CANCEL("Cancel", "Refund request cancelled");

    private String code;
    private String label;

}
