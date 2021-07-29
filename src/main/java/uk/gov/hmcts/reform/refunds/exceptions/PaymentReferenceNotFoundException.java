package uk.gov.hmcts.reform.refunds.exceptions;

public class PaymentReferenceNotFoundException  extends RuntimeException {
    PaymentReferenceNotFoundException(){

    }

    public PaymentReferenceNotFoundException(String message) {
        super(message);
    }
}
