package uk.gov.hmcts.reform.refunds.exceptions;

public class InvalidRefundReviewRequestException extends RuntimeException {

    public static final long serialVersionUID = 313287432;

    public InvalidRefundReviewRequestException(String message) {
        super(message);
    }

}
