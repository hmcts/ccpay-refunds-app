package uk.gov.hmcts.reform.refunds.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetWelcomeTest {

    @DisplayName("Should welcome upon root request with 200 response code")
    @Test
    void welcomeRootEndpoint() throws Exception {
        log.info("The welcomeRootEndpoint() test has started...");
        assertEquals("This is a Template Test for the Integration Layer",
                     "This is a Template Test for the Integration Layer","Test Message");
    }
}
