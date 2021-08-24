package uk.gov.hmcts.reform.refunds.exceptions;

public class PaymentInvalidRequestException extends RuntimeException{

    public static final long serialVersionUID = 413287435;

    public PaymentInvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
