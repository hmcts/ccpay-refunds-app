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
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserListResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_USER_ID1;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class IdamServiceTest {

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
    void getResponseOnValidToken() throws Exception {

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

        String idamUserIdResponse = idamService.getUserId(header);
        assertEquals(mockIdamUserIdResponse.getUid(), idamUserIdResponse);
    }

    @Test
    void getExceptionOnInvalidToken() throws Exception {

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
    void getExceptionOnTokenReturnsNullResponse() throws Exception {

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
    void validateResponseDto() throws Exception {
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
    void givenNoIDAMResponse_whenGetUserIdSetForService_thenUserNotFoundException() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");
        ResponseEntity<IdamUserListResponse> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserListResponse.class)
        )).thenReturn(responseEntity);

        Assertions.assertThrows(UserNotFoundException.class,
                () -> idamService.getUserIdSetForService(header, roles));

    }

    @Test
    void givenIDAMResponse_whenGetUserIdSetForService_thenDistinctUserIdSetIsReceived() {
        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("authorization", Collections.singletonList("Bearer 131313"));
        List<String> roles = new ArrayList<>();
        roles.add("damage");
        IdamUserInfoResponse user1 = IdamUserInfoResponse.idamFullNameRetrivalResponseWith().id("AA")
                .roles(Arrays.asList("caseworker-probate", "caseworker-damage")).build();
        IdamUserInfoResponse user2 = IdamUserInfoResponse.idamFullNameRetrivalResponseWith().id("BB")
                .roles(Arrays.asList("caseworker-probate", "payments-refund")).build();
        IdamUserInfoResponse user3 = IdamUserInfoResponse.idamFullNameRetrivalResponseWith().id("CC")
                .roles(Arrays.asList("payments-refund", "caseworker-damage")).build();
        IdamUserListResponse response = IdamUserListResponse.idamUserListResponseWith()
                .idamUserInfoResponseList(Arrays.asList(user1, user2, user3)).build();
        ResponseEntity<IdamUserListResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(IdamUserListResponse.class)
        )).thenReturn(responseEntity);

        Set<String> users = idamService.getUserIdSetForService(header, roles);

        Assertions.assertTrue(users.contains("AA"));
        Assertions.assertTrue(users.contains("CC"));

    }

}
