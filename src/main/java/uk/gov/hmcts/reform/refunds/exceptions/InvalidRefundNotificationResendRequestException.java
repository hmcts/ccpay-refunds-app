package uk.gov.hmcts.reform.refunds.exceptions;

public class InvalidRefundNotificationResendRequestException extends RuntimeException {

    public static final long serialVersionUID = 423287432;

    public InvalidRefundNotificationResendRequestException(String message) {
        super(message);
    }

    public InvalidRefundNotificationResendRequestException(String message, Throwable throwable) {
        super(message,throwable);
    }
}
