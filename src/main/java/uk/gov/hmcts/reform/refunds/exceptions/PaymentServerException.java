package uk.gov.hmcts.reform.refunds.exceptions;

public class PaymentServerException extends RuntimeException {

    public static final long serialVersionUID = 413287436;

    public PaymentServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentServerException(String message) {
        super(message);
    }
}
