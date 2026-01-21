package uk.gov.hmcts.reform.refunds.functional.util;

public enum PaymentMethodType {

    CARD("card"),
    PBA("payment by account"),
    CASH("cash"),
    CHEQUE("cheque"),
    POSTAL_ORDER("postal order"),
    BARCLAY_CARD("barclay card"),
    ALL("all");

    String type;

    PaymentMethodType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
