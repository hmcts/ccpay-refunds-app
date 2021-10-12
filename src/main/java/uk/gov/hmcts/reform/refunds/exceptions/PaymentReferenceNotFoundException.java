package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class PaymentReferenceNotFoundException  extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 43287434;

    public PaymentReferenceNotFoundException(String message) {
        super(message);
    }

    public PaymentReferenceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
