package uk.gov.hmcts.reform.refunds.exceptions;

public class LiberataInvalidRequestException extends RuntimeException{

    public static final long serialVersionUID = 413287433;

    public LiberataInvalidRequestException(String message) {
        super(message);
    }

    public LiberataInvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

}
