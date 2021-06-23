package uk.gov.hmcts.reform.refunds.config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;

import static org.hamcrest.Matchers.notNullValue;

public class SwaggerConfigurationTest {

    @Test
    public void docketBean() {
        assertThat(new SwaggerConfiguration().api(), is(notNullValue()));
    }
}
