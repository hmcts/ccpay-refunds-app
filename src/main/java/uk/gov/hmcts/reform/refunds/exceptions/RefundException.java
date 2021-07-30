package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundException extends RuntimeException {

    public RefundException() {
    }

    public RefundException(String message) {
        super(message);
    }

    public RefundException(String message, Throwable cause) {
        super(message, cause);
    }

    public RefundException(Throwable cause) {
        super(cause);
    }

    public RefundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);

    }

}
