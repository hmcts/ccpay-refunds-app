package uk.gov.hmcts.reform.refunds.exceptions;

public class LiberataServerException extends RuntimeException{

    public static final long serialVersionUID = 413287434;

    public LiberataServerException(String message, Throwable cause) {
        super(message, cause);
    }

}
