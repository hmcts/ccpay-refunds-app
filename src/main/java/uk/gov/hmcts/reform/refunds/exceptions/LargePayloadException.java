package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class LargePayloadException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 413287439;

    public LargePayloadException(String message) {
        super(message);
    }
}
