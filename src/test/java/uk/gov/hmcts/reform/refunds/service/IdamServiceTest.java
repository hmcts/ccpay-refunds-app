package uk.gov.hmcts.reform.refunds.service;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_USER_ID1;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class IdamServiceTest {

    public static final IdamUserInfoResponse USER1 = IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id("AA")
            .email("aa@gmail.com")
            .forename("AAA")
            .surname("BBB")
            .roles(List.of("caseworker-refund", "caseworker-damage"))
            .active(true)
            .lastModified("2021-02-20T11:03:08.067Z")
            .stale(false)
            .createDate("2021-01-20T11:03:08.067Z")
            .build();
    public static final IdamUserInfoResponse USER2 = IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id("BB")
            .email("bb@gmail.com")
            .forename("BBB")
            .surname("CCC")
            .roles(List.of("caseworker-probate", "payments-refund"))
            .active(true)
            .lastModified("2021-03-20T11:03:08.067Z")
            .stale(false)
            .createDate("2021-01-20T11:03:08.067Z")
            .build();
    public static final IdamUserInfoResponse USER3 = IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id("CC")
            .email("cc@gmail.com")
            .forename("CCC")
            .surname("DDD")
            .roles(List.of("payments-refund", "caseworker-damage"))
            .active(true)
            .lastModified("2021-04-20T11:03:08.067Z")
            .stale(false)
            .createDate("2021-01-20T11:03:08.067Z")
            .build();

    @InjectMocks
    private IdamServiceImpl idamService;

    @Mock
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getResponseOnValidToken() {

        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));

        IdamUserIdResponse mockIdamUserIdResponse = IdamUserIdResponse.idamUserIdResponseWith()
            .familyName("VP")
            .givenName("VP")
            .name("VP")
            .sub("V_P@gmail.com")
            .roles(Arrays.asList("vp"))
            .uid("986-erfg-kjhg-123")
            .build();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        IdamUserIdResponse idamUserIdResponse = idamService.getUserId(header);
        assertEquals(mockIdamUserIdResponse.getUid(), idamUserIdResponse.getUid());
    }

    @Test
    void getExceptionOnInvalidToken() {

        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "user not found"));

        assertThrows(UserNotFoundException.class, () -> {
            idamService.getUserId(header);
        });
    }

    @Test
    void getExceptionOnTokenReturnsNullResponse() {

        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        assertThrows(UserNotFoundException.class, () -> {
            idamService.getUserId(header);
        });
    }

    @Test
    void getExceptionOnValidToken() throws Exception {

        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "Gateway timeout"));
        assertThrows(GatewayTimeoutException.class, () -> {
            idamService.getUserId(header);
        });
    }

    @Test
    void testGetUserFullNameExceptionScenarios() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserInfoResponse[].class)
        )).thenThrow(new UserNotFoundException("User Not Found"));

        assertThrows(UserNotFoundException.class, () -> {
            idamService.getUserIdentityData(header, GET_REFUND_LIST_CCD_CASE_USER_ID1);
        });
    }

    @Test
    void givenLibrataNameUID_whenGetUserIdentityData_thenLibrataNameIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");

        final IdamUserInfoResponse[] responses = {USER1};
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(responses, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        UserIdentityDataDto resultDto = idamService.getUserIdentityData(header, "Middle office provider");

        Assertions.assertTrue(resultDto.getFullName().contains("Middle office provider"));
    }

    @Test
    void givenIDAMResponse_whenGetUserIdentityData_thenDistinctUserIdSetIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");

        final IdamUserInfoResponse[] responses = {USER1};
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(responses, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        UserIdentityDataDto resultDto = idamService.getUserIdentityData(header, "AA");

        assertEquals("AAA BBB", resultDto.getFullName());
        assertEquals("aa@gmail.com", resultDto.getEmailId());
    }

    @Test
    void givenNoIDAMResponse_whenGetUserIdentityData_thenUserNotFoundExceptionIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");

        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        Exception exception = Assertions.assertThrows(UserNotFoundException.class,
                () -> idamService.getUserIdentityData(header, "AA"));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("User details not found for these roles in IDAM"));
    }

    @Test
    void validateResponseDto() {
        IdamUserIdResponse idamUserIdResponse = IdamUserIdResponse.idamUserIdResponseWith()
            .familyName("VP")
            .givenName("VP")
            .name("VP")
            .sub("V_P@gmail.com")
            .roles(Arrays.asList("vp"))
            .uid("986-erfg-kjhg-123")
            .build();

        assertEquals("VP", idamUserIdResponse.getFamilyName());
        assertEquals("VP", idamUserIdResponse.getGivenName());
        assertEquals("VP", idamUserIdResponse.getName());
        assertEquals(Arrays.asList("vp"), idamUserIdResponse.getRoles());
        assertEquals("986-erfg-kjhg-123", idamUserIdResponse.getUid());
        assertEquals("V_P@gmail.com", idamUserIdResponse.getSub());
    }

    @Test
    void givenNoIDAMResponse_whenGetUsersForRoles_thenUserNotFoundException() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        Assertions.assertThrows(UserNotFoundException.class,
                () -> idamService.getUsersForRoles(header, roles));

    }

    @Test
    void givenSingleRole_whenGetUsersForRoles_thenDistinctUserIdSetIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("caseworker-damage");

        IdamUserInfoResponse[] response = {USER1, USER2, USER3};
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        List
                <UserIdentityDataDto> users = idamService.getUsersForRoles(header, roles);

        assertEquals(3, users.size());
        assertEquals("AAA BBB", users.get(0).getFullName());

    }

    @Test
    void givenMultipleRoles_whenGetUsersForRoles_thenDistinctUserIdSetIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("caseworker-damage");
        roles.add("caseworker-probate");

        IdamUserInfoResponse[] response = {USER1, USER2, USER3};
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        List <UserIdentityDataDto> users = idamService.getUsersForRoles(header, roles);

        assertEquals(3, users.size());
        assertEquals("AAA BBB", users.get(0).getFullName());

    }

    @Test
    void givenNoRoles_whenGetUsersForRoles_thenDistinctUserIdSetIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("caseworker-damage");
        roles.add("caseworker-probate");

        IdamUserInfoResponse[] response = {USER1, USER2, USER3};
        ResponseEntity<IdamUserInfoResponse[]> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);

        List <UserIdentityDataDto> users = idamService.getUsersForRoles(header, Arrays.asList());

        assertEquals(3, users.size());
        assertEquals("AAA BBB", users.get(0).getFullName());

    }

}
