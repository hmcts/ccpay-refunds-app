package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundFeeNotFoundInPaymentException extends RuntimeException {
    public static final long serialVersionUID = 433287436;

    public RefundFeeNotFoundInPaymentException(String message) {
        super(message);
    }

}
