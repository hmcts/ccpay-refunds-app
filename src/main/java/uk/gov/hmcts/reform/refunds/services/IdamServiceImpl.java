package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpHeaders.EMPTY;

@Service
@SuppressWarnings("PMD.PreserveStackTrace")
public class IdamServiceImpl implements IdamService {

    public static final String USERID_ENDPOINT = "/o/userinfo";
    public static final String USER_FULL_NAME_ENDPOINT = "/api/v1/users";
    private static final String TOKEN_ENDPOINT = "/o/token";
    private static final Logger LOG = LoggerFactory.getLogger(IdamServiceImpl.class);
    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String LIBERATA_SYSTEM_USER_NAME = "System user";
    private static final String INTERNAL_SERVER_ERROR_MSG = "Internal Server error. Please, try again later";
    private static final String USER_DETAILS_NOT_FOUND_ERROR_MSG = "User details not found for these roles in IDAM";

    @Value("${idam.api.url}")
    private String idamBaseUrl;

    @Value("${user.info.size}")
    private String userInfoSize;

    @Value("${user.lastModifiedTime}")
    private String lastModifiedTime;

    @Autowired()
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @Value("${refunds.serviceAccount.clientId}")
    private String serviceClientId;

    @Value("${refunds.serviceAccount.clientSecret}")
    private String serviceClientSecret;

    @Value("${refunds.serviceAccount.grantType}")
    private String serviceGrantType;

    @Value("${refunds.serviceAccount.username}")
    private String serviceUsername;

    @Value("${refunds.serviceAccount.password}")
    private String servicePassword;

    @Value("${refunds.serviceAccount.scope}")
    private String serviceScope;

    @Value("${refunds.serviceAccount.redirectUri}")
    private String redirectUri;


    @Override
    public IdamUserIdResponse getUserId(MultiValueMap<String, String> headers) {
        try {
            ResponseEntity<IdamUserIdResponse> responseEntity = getResponseEntity(headers);
            if (responseEntity != null) {
                IdamUserIdResponse idamUserIdResponse = responseEntity.getBody();
                if (idamUserIdResponse != null) {
                    return idamUserIdResponse;
                }
            }
            LOG.error("Parse error user not found");
            throw new UserNotFoundException(USER_DETAILS_NOT_FOUND_ERROR_MSG);
        } catch (HttpClientErrorException e) {
            LOG.error("client err ", e);
            throw new UserNotFoundException(INTERNAL_SERVER_ERROR_MSG);
        } catch (HttpServerErrorException e) {
            LOG.error("server err ", e);
            throw new GatewayTimeoutException("Unable to retrieve User information. Please try again later");
        }
    }

    private ResponseEntity<IdamUserIdResponse> getResponseEntity(MultiValueMap<String, String> headers) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idamBaseUrl + USERID_ENDPOINT);
        LOG.info("builder.toUriString() in getResponseEntity : {}", builder.toUriString());
        return restTemplateIdam
            .exchange(
                builder.toUriString(),
                HttpMethod.GET,
                getEntity(headers), IdamUserIdResponse.class
            );
    }

    private HttpEntity<String> getEntity(MultiValueMap<String, String> headers) {
        MultiValueMap<String, String> headerMultiValueMap = new LinkedMultiValueMap<>();
        headerMultiValueMap.put(
            "Content-Type",
            headers.get("content-type") == null ? List.of("application/json") : headers.get("content-type")
        );
        String userAuthorization =
            headers.get("authorization") == null ? headers.get("Authorization").get(0) : headers.get(
                "authorization").get(0);
        headerMultiValueMap.put(
            "Authorization", Collections.singletonList(userAuthorization.startsWith("Bearer ")
                                                           ? userAuthorization : "Bearer ".concat(userAuthorization))
        );
        HttpHeaders httpHeaders = new HttpHeaders(headerMultiValueMap);
        return new HttpEntity<>(httpHeaders);
    }


    @Override
    public UserIdentityDataDto getUserIdentityData(MultiValueMap<String, String> headers, String uid) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idamBaseUrl + USER_FULL_NAME_ENDPOINT)
            .queryParam("query", "id:" + uid);
        LOG.info("builder.toUriString() getUserIdentityData : {}", builder.toUriString());

        if (LIBERATA_NAME.equals(uid) || LIBERATA_SYSTEM_USER_NAME.equalsIgnoreCase(uid)) {
            return UserIdentityDataDto.userIdentityDataWith()
                .fullName(uid)
                .build();
        }

        ResponseEntity<IdamUserInfoResponse[]> idamFullNameResEntity = restTemplateIdam
            .exchange(
                builder.toUriString(),
                HttpMethod.GET,
                getEntity(headers), IdamUserInfoResponse[].class
            );

        if (idamFullNameResEntity != null && idamFullNameResEntity.getBody() != null) {
            IdamUserInfoResponse[] idamArrayFullNameRetrievalResponse = idamFullNameResEntity.getBody();

            if (idamArrayFullNameRetrievalResponse != null && idamArrayFullNameRetrievalResponse.length > 0) {
                IdamUserInfoResponse idamUserInfoResponse = idamArrayFullNameRetrievalResponse[0];
                return UserIdentityDataDto.userIdentityDataWith()
                    .id(idamUserInfoResponse.getId())
                    .roles(idamUserInfoResponse.getRoles())
                    .emailId(idamUserInfoResponse.getEmail())
                    .fullName(idamUserInfoResponse.getForename() + " " + idamUserInfoResponse.getSurname())
                    .build();
            }
        }

        LOG.error("User name not found for given user id : {}", uid);
        throw new UserNotFoundException(USER_DETAILS_NOT_FOUND_ERROR_MSG);
    }

    @Override
    public List<UserIdentityDataDto> getUsersForRoles(MultiValueMap<String, String> headers, List<String> roles) {
        List<UserIdentityDataDto> userIdentityDataDtoList = new ArrayList<>();

        String query = getRoles(roles) + ") AND lastModified:>now-" + lastModifiedTime;

        UriComponents builder = UriComponentsBuilder.newInstance()
            .fromUriString(idamBaseUrl + USER_FULL_NAME_ENDPOINT)
            .query("query={query}")
            .query("size={size}")
            .buildAndExpand(query, userInfoSize);

        LOG.info("builder.toUriString(): {}", builder.toUriString());

        ResponseEntity<IdamUserInfoResponse[]> idamUserListResponseEntity = restTemplateIdam
            .exchange(
                builder.toUriString(),
                HttpMethod.GET,
                getEntity(headers), IdamUserInfoResponse[].class
            );
        if (idamUserListResponseEntity != null && idamUserListResponseEntity.getBody() != null) {
            IdamUserInfoResponse[] idamUserListResponse = idamUserListResponseEntity.getBody();

            for (IdamUserInfoResponse idamUserInfoResponse : idamUserListResponse) {

                userIdentityDataDtoList.add(UserIdentityDataDto.userIdentityDataWith()
                                                .id(idamUserInfoResponse.getId())
                                                .emailId(idamUserInfoResponse.getEmail())
                                                .roles(idamUserInfoResponse.getRoles())
                                                .fullName(idamUserInfoResponse.getForename() + " " + idamUserInfoResponse.getSurname())
                                                .build());
            }

            return userIdentityDataDtoList;
        }

        LOG.error(USER_DETAILS_NOT_FOUND_ERROR_MSG);
        throw new UserNotFoundException(USER_DETAILS_NOT_FOUND_ERROR_MSG);
    }

    @Override
    public IdamTokenResponse getSecurityTokens() {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
            .fromUriString(idamBaseUrl + TOKEN_ENDPOINT)
            .queryParam("client_id",serviceClientId)
            .queryParam("client_secret",serviceClientSecret)
            .queryParam("grant_type",serviceGrantType)
            .queryParam("password",servicePassword)
            .queryParam("redirect_uri",redirectUri)
            .queryParam("scope",serviceScope)
            .queryParam("username",serviceUsername);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<IdamTokenResponse> idamTokenResponse = restTemplateIdam
                                                                .exchange(
                                                                    builder.build(false).toUriString(),
                                                                    HttpMethod.POST,
                                                                    new HttpEntity<>(httpHeaders,EMPTY),
                                                                    IdamTokenResponse.class
                                                                );


        return idamTokenResponse.getBody();
    }

    private StringBuilder getRoles(List<String> roles) {
        StringBuilder rolesValue = new StringBuilder("(");
        if (!roles.isEmpty()) {
            for (String role : roles) {
                rolesValue.append("roles:").append(role).append(" OR ");
            }

            // Add the refund base role - "payments-refund" for IDAM API
            return roles.contains("payments-refund") && roles.contains("payments-refund-approver") ? rolesValue
                .replace(rolesValue.length() - 4, rolesValue.length(), "") : roles.contains("payments-refund-approver") ? rolesValue
                .append("roles:payments-refund") : rolesValue.append("roles:payments-refund-approver");
        }
        return new StringBuilder("");
    }
}
