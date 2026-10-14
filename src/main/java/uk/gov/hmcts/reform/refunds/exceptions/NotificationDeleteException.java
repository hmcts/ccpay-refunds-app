package uk.gov.hmcts.reform.refunds.exceptions;

public class NotificationDeleteException extends RuntimeException {

    public static final long serialVersionUID = 592384759201L;

    public NotificationDeleteException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeleteException(String message) {
        super(message);
    }
}
