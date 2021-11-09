package uk.gov.hmcts.reform.refunds.exceptions;

public class PaymentReferenceNotFoundException  extends RuntimeException {

    public static final long serialVersionUID = 43287434;

    public PaymentReferenceNotFoundException(String message) {
        super(message);
    }

    public PaymentReferenceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
