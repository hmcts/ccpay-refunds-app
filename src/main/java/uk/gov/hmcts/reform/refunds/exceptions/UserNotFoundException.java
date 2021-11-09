package uk.gov.hmcts.reform.refunds.exceptions;

public class UserNotFoundException extends RuntimeException {

    public static final long serialVersionUID = 43287431;

    public UserNotFoundException(String message) {
        super(message);
    }

}
