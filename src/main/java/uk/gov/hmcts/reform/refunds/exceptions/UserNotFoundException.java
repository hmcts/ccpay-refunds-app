package uk.gov.hmcts.reform.refunds.exceptions;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
    }

    public UserNotFoundException(String message) {
        super(message);
    }

}
