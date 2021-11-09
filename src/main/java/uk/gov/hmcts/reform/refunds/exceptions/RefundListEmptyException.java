package uk.gov.hmcts.reform.refunds.exceptions;


public class RefundListEmptyException extends RuntimeException {

    public static final long serialVersionUID = 413287434;

    public RefundListEmptyException(String message) {
        super(message);
    }

}
