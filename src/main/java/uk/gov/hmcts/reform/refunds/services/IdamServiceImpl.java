package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
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
    static private final String LIBERATA_NAME = "Middle office provider";
    private static final String INTERNAL_SERVER_ERROR_MSG = "Internal Server error. Please, try again later";
    private static final String USER_DETAILS_NOT_FOUND_ERROR_MSG = "User details not found for these roles in IDAM";

    @Value("${idam.api.url}")
    private String idamBaseURL;

    @Value("${user.info.size}")
    private String userInfoSize;

    @Value("${user.lastModifiedTime}")
    private String lastModifiedTime;

    @Autowired()
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;


    @Override
    public IdamUserIdResponse getUserId(MultiValueMap<String, String> headers) {
//        return IdamUserIdResponse.idamUserIdResponseWith().uid("1").givenName("XX").familyName("YY").name("XX YY")
//            .roles(Arrays.asList("payments-refund-approver", "payments-refund")).sub("ZZ").
//                build();
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
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idamBaseURL + USERID_ENDPOINT);
        LOG.debug("builder.toUriString() : {}", builder.toUriString());
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
//        return UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name").emailId(
//            "h@mail.com").id("1").build();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idamBaseURL + USER_FULL_NAME_ENDPOINT)
            .queryParam("query", "id:" + uid);
        LOG.debug("builder.toUriString() : {}", builder.toUriString());

        if (LIBERATA_NAME.equals(uid)) {
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
//        return Collections.singletonList(UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name").emailId(
//            "h@mail.com").id("1").build());
        String idamBaseURL = "https://idam-api.demo.platform.hmcts.net/";
        List<UserIdentityDataDto> userIdentityDataDtoList = new ArrayList<>();

        String query = getRoles(roles) + ") AND lastModified:>now-" + lastModifiedTime;

        UriComponents builder = UriComponentsBuilder.newInstance()
            .fromUriString(idamBaseURL + USER_FULL_NAME_ENDPOINT)
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
        LOG.info("idamUserListResponseEntity: {}", idamUserListResponseEntity);
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

            LOG.info("userIdentityDataDtoList: {}", userIdentityDataDtoList);
            return userIdentityDataDtoList;
        }

        LOG.error(USER_DETAILS_NOT_FOUND_ERROR_MSG);
        throw new UserNotFoundException(USER_DETAILS_NOT_FOUND_ERROR_MSG);
    }

    @Override
    public IdamTokenResponse getSecurityTokens() {
        String idamBaseURL = "https://idam-api.demo.platform.hmcts.net/";
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
            .fromUriString(idamBaseURL + TOKEN_ENDPOINT)
            .queryParam("client_id","refunds_api")
            .queryParam("client_secret","2q6VLB39Lx23Zg9G")
            .queryParam("grant_type","password")
            .queryParam("password","PassRefund123")
            .queryParam("redirect_uri","http://ccpay-refunds-api-demo.service.core-compute-demo.internal/oauth2/callback")
            .queryParam("scope","openid profile roles search-user")
            .queryParam("username","idam.user.ccpayrefundsapi@hmcts.net");
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        System.out.println(builder.toUriString());
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
            return roles.contains("payments-refund") && roles.contains("payments-refund-approver")? rolesValue
                .replace(rolesValue.length() - 4, rolesValue.length(), "") : roles.contains("payments-refund-approver") ? rolesValue
                .append("roles:payments-refund") : rolesValue.append("roles:payments-refund-approver");
        }
        return new StringBuilder("");
    }
}
