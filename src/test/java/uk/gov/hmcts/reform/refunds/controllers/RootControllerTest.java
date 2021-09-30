package uk.gov.hmcts.reform.refunds.controllers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
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

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles({"local", "test"})
public class RootControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Before
    public void setUp() {
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
    }


    @Test
    public void should_return_welcome_message_with_feature_enabled() throws Exception {
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        ResultActions resultActions = mockMvc.perform(get("/refundstest")
                                                          .header("Authorization", "user")
                                                          .header("ServiceAuthorization", "service")
                                                          .accept(MediaType.APPLICATION_JSON));
        assertEquals(200, resultActions.andReturn().getResponse().getStatus());
        assertEquals("Welcome to refunds with feature enabled", resultActions.andReturn().getResponse().getContentAsString());
    }

    @Test
    public void should_return_welcome_message_with_feature_false() throws Exception {
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(false);
        ResultActions resultActions = mockMvc.perform(get("/refundstest")
                                                          .header("Authorization", "user")
                                                          .header("ServiceAuthorization", "service")
                                                          .accept(MediaType.APPLICATION_JSON));
        assertEquals(200, resultActions.andReturn().getResponse().getStatus());
        assertEquals(
            "Welcome to refunds with feature false",
            resultActions.andReturn().getResponse().getContentAsString()
        );
    }

}
