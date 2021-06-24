package uk.gov.hmcts.reform.refunds.controllers;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;

public class RootControllerTest {

    private RootController rootController = new RootController();

    @Test
    public void should_return_welcome_message() {

        ResponseEntity<String> response = rootController.welcome();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Welcome to ccpay-refunds-app", response.getBody());
    }

}
