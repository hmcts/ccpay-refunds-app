package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class ReissueExpiredRefundException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 413287439;

    public ReissueExpiredRefundException(String message) {
        super(message);
    }
}
