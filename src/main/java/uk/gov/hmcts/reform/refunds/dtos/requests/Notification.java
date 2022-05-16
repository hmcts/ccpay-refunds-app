package uk.gov.hmcts.reform.refunds.dtos.requests;

public enum Notification {

    EMAIL("EMAIL"),
    LETTER("LETTER");

    private String notification;

    Notification(String notification) {
        this.notification = notification;
    }

    public String getNotification() {
        return this.notification;
    }
}
