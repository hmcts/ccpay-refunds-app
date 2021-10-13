package uk.gov.hmcts.reform.refunds.exceptions;

public class ForbiddenToApproveRefundException extends RuntimeException {
    public static final long serialVersionUID = 343287436;

    public ForbiddenToApproveRefundException(String message) {
        super(message);
    }
}
