package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundNotFoundException extends RuntimeException{
    public static final long serialVersionUID = 413287436;

    public RefundNotFoundException(String message) {
        super(message);
    }

}
