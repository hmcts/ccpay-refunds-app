package uk.gov.hmcts.reform.refunds.dtos.requests;

public enum RefundStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private String code;

    RefundStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
