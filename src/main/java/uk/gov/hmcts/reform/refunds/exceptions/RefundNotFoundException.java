package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundNotFoundException extends RefundException{

    public RefundNotFoundException() {}

    public RefundNotFoundException(String message) {
        super(message);
    }
}


