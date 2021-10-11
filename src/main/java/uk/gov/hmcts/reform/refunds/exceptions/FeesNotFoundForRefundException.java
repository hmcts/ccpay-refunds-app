package uk.gov.hmcts.reform.refunds.exceptions;

public class FeesNotFoundForRefundException extends RuntimeException {
    public static final long serialVersionUID = 433287436;

    public FeesNotFoundForRefundException(String message) {
        super(message);
    }

}

