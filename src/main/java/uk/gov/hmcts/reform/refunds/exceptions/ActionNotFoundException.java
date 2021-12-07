package uk.gov.hmcts.reform.refunds.exceptions;

public class ActionNotFoundException extends RuntimeException {
    public static final long serialVersionUID = 333287436;

    public ActionNotFoundException(String message) {
        super(message);
    }

}
