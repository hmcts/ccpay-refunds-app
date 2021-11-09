package uk.gov.hmcts.reform.refunds.exceptions;

public class InvalidRefundRequestException extends RuntimeException {

    public static final long serialVersionUID = 413287432;

    public InvalidRefundRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidRefundRequestException(String message) {
        super(message);
    }

}
