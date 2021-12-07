package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundReasonNotFoundException extends RuntimeException {
    public static final long serialVersionUID = 413287439;

    public RefundReasonNotFoundException(String message) {
        super(message);
    }

}
