package uk.gov.hmcts.reform.refunds.exceptions;

public class InvalidRefundRequestException extends RuntimeException {

    public InvalidRefundRequestException() {
    }

    public InvalidRefundRequestException(String message) {
        super(message);
    }

}
