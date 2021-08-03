package uk.gov.hmcts.reform.refunds.exceptions;

public class GatewayTimeoutException extends RuntimeException{

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
