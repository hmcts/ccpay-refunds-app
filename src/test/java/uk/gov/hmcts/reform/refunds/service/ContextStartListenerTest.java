package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.services.IdamService;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"local", "test"})
public class ContextStartListenerTest {

    @MockBean
    private IdamService idamService;

    @Autowired
    private ContextStartListener contextStartListener;

    @Autowired
    private ConfigurableApplicationContext configurableApplicationContext;

    @BeforeEach
    public void setUp(){
        when(idamService.getUsersForRoles(any(),eq(Arrays.asList("payments-refund","payments-refund-approver")))).thenReturn(Arrays.asList(UserIdentityDataDto.userIdentityDataWith().id("1f2b7025-0f91-4737-92c6-b7a9baef14c6")
                                                                                     .fullName("mock-Forename mock-Surname").emailId("mockfullname@gmail.com").build()));

        when(idamService.getSecurityTokens()).thenReturn(IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("access token").build());
    }

    @Test
    public void shouldCallIdamServiceGetUsersForRolesWhenApplicationContextStarts(){
        configurableApplicationContext.start();
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.add("Authorization", "access token");
        verify(idamService).getUsersForRoles(inputHeaders,Arrays.asList("payments-refund","payments-refund-approver"));
    }

}
