package uk.gov.hmcts.reform.refunds.exceptions;

public class ReconciliationProviderServerException extends RuntimeException{

    public static final long serialVersionUID = 413287434;

    public ReconciliationProviderServerException(String message, Throwable cause) {
        super(message, cause);
    }

}
