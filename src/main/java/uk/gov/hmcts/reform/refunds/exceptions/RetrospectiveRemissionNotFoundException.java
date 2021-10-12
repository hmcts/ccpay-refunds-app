package uk.gov.hmcts.reform.refunds.exceptions;

public class RetrospectiveRemissionNotFoundException extends RuntimeException {
    public static final long serialVersionUID = 423287436;

    public RetrospectiveRemissionNotFoundException(String message) {
        super(message);
    }

}


