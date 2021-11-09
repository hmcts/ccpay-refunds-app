package uk.gov.hmcts.reform.refunds.exceptions;

public class GatewayTimeoutException extends RuntimeException {

    public static final long serialVersionUID = 43287432;

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
