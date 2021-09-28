package uk.gov.hmcts.reform.refunds.util;

public enum CurrencyCode {
    GBP("GBP");

    private String code;

    CurrencyCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
