package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class RefundEmptyException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 413287434;

    public RefundEmptyException(String message) {
        super(message);
    }

}
