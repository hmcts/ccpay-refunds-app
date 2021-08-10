package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class NoRejectReasonFoundException extends RuntimeException implements Serializable {

    public NoRejectReasonFoundException(String message) {
        super(message);
    }

}
