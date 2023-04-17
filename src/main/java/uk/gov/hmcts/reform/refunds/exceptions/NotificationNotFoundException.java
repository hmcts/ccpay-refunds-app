package uk.gov.hmcts.reform.refunds.exceptions;

public class NotificationNotFoundException extends RuntimeException {
    public static final long serialVersionUID = 413287436;

    public NotificationNotFoundException(String message) {
        super(message);
    }

}
