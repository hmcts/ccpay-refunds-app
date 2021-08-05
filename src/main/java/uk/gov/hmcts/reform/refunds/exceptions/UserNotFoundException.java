package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class UserNotFoundException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 43287431;
    public UserNotFoundException(String message) {
        super(message);
    }

}
