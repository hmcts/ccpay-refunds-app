package uk.gov.hmcts.reform.refunds.exceptions;

public class ReconciliationProviderInvalidRequestException extends RuntimeException {

    public static final long serialVersionUID = 413287433;

    public ReconciliationProviderInvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

}
