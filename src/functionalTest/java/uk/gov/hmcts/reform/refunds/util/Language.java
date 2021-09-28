package uk.gov.hmcts.reform.refunds.util;

public enum Language {
    CY("cy");

    private String language;

    Language(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return this.language;
    }
}
