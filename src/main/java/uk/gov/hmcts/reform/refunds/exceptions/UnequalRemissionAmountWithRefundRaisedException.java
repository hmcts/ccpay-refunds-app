package uk.gov.hmcts.reform.refunds.exceptions;

public class UnequalRemissionAmountWithRefundRaisedException extends RuntimeException {
    public static final long serialVersionUID = 423287436;

    public UnequalRemissionAmountWithRefundRaisedException(String message) {
        super(message);
    }

}
