package uk.gov.hmcts.reform.refunds.config.toggler;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LaunchDarklyFeatureToggler implements FeatureToggler {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchDarklyFeatureToggler.class);

    @Value("${launch.darkly.user.name}")
    private String userName;

    private final LDClientInterface ldClient;

    public LaunchDarklyFeatureToggler(LDClientInterface ldClient) {
        this.ldClient = ldClient;
    }

    @Override
    public boolean getBooleanValue(String key, Boolean defaultValue) {

        LOG.info("userName in LaunchDarklyFeatureToggler: {}", userName);
        LDContext context = LDContext.create(userName);

        return ldClient.boolVariation(
            key,
            context,
            defaultValue
        );
    }

}
