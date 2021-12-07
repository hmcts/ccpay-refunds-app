package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class InvalidRefundRequestException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 413287432;

    public InvalidRefundRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidRefundRequestException(String message) {
        super(message);
    }

}
