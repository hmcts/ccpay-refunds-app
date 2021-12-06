package uk.gov.hmcts.reform.refunds.exceptions;

public class ActionNotAllowedException extends RuntimeException {
    public static final long serialVersionUID = 333297436;

    public ActionNotAllowedException(String message) {
        super(message);
    }

}
