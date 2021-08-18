package uk.gov.hmcts.reform.refunds.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RootControllerTest {

    MockMvc mockMvc;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    public void setUp() {
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
    }


    @Test
    void should_return_welcome_message_with_feature_enabled() throws Exception {
        log.info("Test : should_return_welcome_message_with_feature_enabled() has started");
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        ResultActions resultActions = mockMvc.perform(get("/refundstest")
                                                          .header("Authorization", "user")
                                                          .header("ServiceAuthorization", "service")
                                                          .accept(MediaType.APPLICATION_JSON));
        assertEquals(200, resultActions.andReturn().getResponse().getStatus());
        assertEquals("Welcome to refunds with feature enabled", resultActions.andReturn().getResponse().getContentAsString());
        log.info("Test : should_return_welcome_message_with_feature_enabled() has completed");
    }

    @Test
    void should_return_welcome_message_with_feature_false() throws Exception {
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(false);
        ResultActions resultActions = mockMvc.perform(get("/refundstest")
                                                          .header("Authorization", "user")
                                                          .header("ServiceAuthorization", "service")
                                                          .accept(MediaType.APPLICATION_JSON));
        assertEquals(200, resultActions.andReturn().getResponse().getStatus());
        assertEquals("Welcome to refunds with feature false", resultActions.andReturn().getResponse().getContentAsString());
    }

   /* @Test
    public void should_return_RefundId() throws Exception {
        Timestamp dateInstant = Timestamp.from(Instant.now());
        when(refundsRepository.save(Mockito.any(Refund.class))).thenReturn(Refund.refundsWith()
                                                                               .id(1)
                                                                               .refundsId("refund-id")
                                                                               .dateCreated(dateInstant)
                                                                               .dateUpdated(dateInstant)
                                                                               .build());
        ResultActions resultActions = mockMvc.perform(post("/refunds")
                                                          .header("Authorization", "user")
                                                          .header("ServiceAuthorization", "service")
                                                          .accept(MediaType.APPLICATION_JSON));
        ObjectMapper objectMapper = new ObjectMapper();

        Refund refund = objectMapper.readValue(resultActions.andReturn().getResponse().getContentAsString(),Refund.class);
        assertEquals(Integer.valueOf(1), refund.getId());
        assertNotNull( refund.getDateCreated());
        assertNotNull(refund.getDateUpdated());
        assertEquals("refund-id",refund.getRefundsId());
    }*/
}
