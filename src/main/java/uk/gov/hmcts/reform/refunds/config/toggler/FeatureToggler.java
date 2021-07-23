package uk.gov.hmcts.reform.refunds.config.toggler;

public interface FeatureToggler {

    boolean getBooleanValue(String key, Boolean defaultValue);

}
