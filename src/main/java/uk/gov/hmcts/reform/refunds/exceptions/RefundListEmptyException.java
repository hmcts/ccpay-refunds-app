package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class RefundListEmptyException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 413287434;

    public RefundListEmptyException(String message) {
        super(message);
    }

}
