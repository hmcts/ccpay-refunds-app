package uk.gov.hmcts.reform.refunds.dtos.requests;

public enum RefundNotificationFlag {

    SENT("SENT"),
    EMAILNOTSENT("EMAIL_NOT_SENT"),
    NOTSENT("NOT_SENT"),
    NOTAPPLICABLE("NOT_APPLICABLE"),
    LETTERNOTSENT("LETTER_NOT_SENT");

    private String flag;

    RefundNotificationFlag(String flag) {
        this.flag = flag;
    }

    public String getFlag() {
        return this.flag;
    }
}
