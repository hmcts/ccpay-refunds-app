package uk.gov.hmcts.reform.refunds.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdamServiceImplTest {

    @InjectMocks
    private IdamServiceImpl idamService;

    @Mock
    private RestTemplate restTemplateIdam;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(idamService, "idamBaseUrl", "http://idam.local");
        ReflectionTestUtils.setField(idamService, "userInfoSize", "25");
        ReflectionTestUtils.setField(idamService, "serviceClientId", "client-id");
        ReflectionTestUtils.setField(idamService, "serviceClientSecret", "client-secret");
        ReflectionTestUtils.setField(idamService, "serviceGrantType", "password");
        ReflectionTestUtils.setField(idamService, "serviceUsername", "svc-user");
        ReflectionTestUtils.setField(idamService, "servicePassword", "svc-pass");
        ReflectionTestUtils.setField(idamService, "serviceScope", "openid profile roles");
        ReflectionTestUtils.setField(idamService, "redirectUri", "http://localhost/redirect");
    }

    @Test
    void getUserIdReturnsBody() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "abc123");

        IdamUserIdResponse response = IdamUserIdResponse.idamUserIdResponseWith().uid("uid-1").build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenReturn(ResponseEntity.ok(response));

        IdamUserIdResponse actual = idamService.getUserId(headers);

        assertEquals("uid-1", actual.getUid());
    }

    @Test
    void getUserIdUsesAuthorizationHeaderAndAddsBearerPrefix() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "raw-token");

        IdamUserIdResponse response = IdamUserIdResponse.idamUserIdResponseWith().uid("uid-1").build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenReturn(ResponseEntity.ok(response));

        idamService.getUserId(headers);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplateIdam).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(IdamUserIdResponse.class));
        HttpEntity capturedEntity = entityCaptor.getValue();
        Object authHeader = capturedEntity.getHeaders().get("Authorization").get(0);
        assertEquals("Bearer raw-token", authHeader);
    }

    @Test
    void getUserIdThrowsUserNotFoundWhenBodyIsNull() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(UserNotFoundException.class, () -> idamService.getUserId(headers));
    }

    @Test
    void getUserIdThrowsUserNotFoundWhenResponseEntityIsNull() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");
        headers.add("content-type", "application/json");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> idamService.getUserId(headers));
    }

    @Test
    void getUserIdThrowsUserNotFoundOnClientError() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(UserNotFoundException.class, () -> idamService.getUserId(headers));
    }

    @Test
    void getUserIdThrowsGatewayTimeoutOnServerError() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserIdResponse.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(GatewayTimeoutException.class, () -> idamService.getUserId(headers));
    }

    @Test
    void getUserIdentityDataReturnsSystemUsersDirectly() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        UserIdentityDataDto actual = idamService.getUserIdentityData(headers, "System user");

        assertEquals("System user", actual.getFullName());
        verify(restTemplateIdam, never()).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                                   eq(IdamUserInfoResponse[].class));
    }

    @Test
    void getUserIdentityDataReturnsFirstIdamUser() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        IdamUserInfoResponse user = IdamUserInfoResponse.idamFullNameRetrivalResponseWith()
            .id("AA")
            .forename("Jane")
            .surname("Doe")
            .email("jane@example.com")
            .roles(List.of("payments-refund"))
            .build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(ResponseEntity.ok(new IdamUserInfoResponse[] {user}));

        UserIdentityDataDto actual = idamService.getUserIdentityData(headers, "AA");

        assertEquals("AA", actual.getId());
        assertEquals("Jane Doe", actual.getFullName());
        assertEquals("jane@example.com", actual.getEmailId());
    }

    @Test
    void getUserIdentityDataThrowsWhenNoUsersReturned() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(ResponseEntity.ok(new IdamUserInfoResponse[] {}));

        assertThrows(UserNotFoundException.class, () -> idamService.getUserIdentityData(headers, "AA"));
    }

    @Test
    void getUserIdentityDataThrowsWhenResponseEntityIsNull() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> idamService.getUserIdentityData(headers, "AA"));
    }

    @Test
    void getUsersForRolesReturnsMappedUsers() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        IdamUserInfoResponse user1 = IdamUserInfoResponse.idamFullNameRetrivalResponseWith()
            .id("AA").forename("A").surname("One").email("a@ex.com").roles(List.of("payments-refund")).build();
        IdamUserInfoResponse user2 = IdamUserInfoResponse.idamFullNameRetrivalResponseWith()
            .id("BB").forename("B").surname("Two").email("b@ex.com").roles(List.of("payments-refund-approver")).build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(ResponseEntity.ok(new IdamUserInfoResponse[] {user1, user2}));

        List<UserIdentityDataDto> result = idamService.getUsersForRoles(headers, List.of("payments-refund"));

        assertEquals(2, result.size());
        assertEquals("A One", result.get(0).getFullName());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplateIdam).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                                          eq(IdamUserInfoResponse[].class));
        assertTrue(urlCaptor.getValue().contains("roles:payments-refund"));
        assertTrue(urlCaptor.getValue().contains("roles:payments-refund-approver"));
    }

    @Test
    void getUsersForRolesThrowsWhenResponseBodyIsNull() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(UserNotFoundException.class, () -> idamService.getUsersForRoles(headers, List.of("payments-refund")));
    }

    @Test
    void getUsersForRolesWithEmptyRolesStillReturnsMappedUsers() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("authorization", "Bearer abc123");

        IdamUserInfoResponse user = IdamUserInfoResponse.idamFullNameRetrivalResponseWith()
            .id("AA").forename("A").surname("One").email("a@ex.com").roles(List.of("payments-refund")).build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(IdamUserInfoResponse[].class)))
            .thenReturn(ResponseEntity.ok(new IdamUserInfoResponse[] {user}));

        List<UserIdentityDataDto> result = idamService.getUsersForRoles(headers, List.of());

        assertEquals(1, result.size());
    }

    @Test
    void getSecurityTokensReturnsBody() {
        IdamTokenResponse token = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("service-token").build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(IdamTokenResponse.class)))
            .thenReturn(ResponseEntity.ok(token));

        IdamTokenResponse actual = idamService.getSecurityTokens();

        assertEquals("service-token", actual.getAccessToken());
    }

    @Test
    void getSecurityTokensWithUsernameAndPasswordUsesProvidedCredentials() {
        IdamTokenResponse token = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("user-token").build();
        when(restTemplateIdam.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(IdamTokenResponse.class)))
            .thenReturn(ResponseEntity.ok(token));

        IdamTokenResponse actual = idamService.getSecurityTokens("lib-user", "lib-pass");

        assertEquals("user-token", actual.getAccessToken());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplateIdam).exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class),
                                          eq(IdamTokenResponse.class));
        assertTrue(urlCaptor.getValue().contains("username=lib-user"));
        assertTrue(urlCaptor.getValue().contains("password=lib-pass"));
    }
}
