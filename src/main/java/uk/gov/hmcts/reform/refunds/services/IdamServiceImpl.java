package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserListResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("PMD.PreserveStackTrace")
public class IdamServiceImpl implements IdamService {

    public static final String USERID_ENDPOINT = "/o/userinfo";
    public static final String USER_FULL_NAME_ENDPOINT = "/api/v1/users";
    private static final Logger LOG = LoggerFactory.getLogger(IdamServiceImpl.class);
    static private final String LIBERATA_NAME = "Middle office provider";
    private static final String INTERNAL_SERVER_ERROR = "Internal Server error. Please, try again later";
    private static final int USER_INFO_SIZE = 100;
    static private final String LAST_MODIFIED_TIME = ">now-720d";

    @Value("${idam.api.url}")
    private String idamBaseURL;

    @Autowired()
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @Override
    public String getUserId(MultiValueMap<String, String> headers) {

//         to test locally
//         return "asdfghjk-kjhgfds-dfghj-sdfghjk";
        try {
            ResponseEntity<IdamUserIdResponse> responseEntity = getResponseEntity(headers);
            if (responseEntity != null) {
                IdamUserIdResponse idamUserIdResponse = responseEntity.getBody();
                if (idamUserIdResponse != null) {
                    return idamUserIdResponse.getUid();
                }
            }
            LOG.error("Parse error user not found");
            throw new UserNotFoundException(INTERNAL_SERVER_ERROR);
        } catch (HttpClientErrorException e) {
            LOG.error("client err ", e);
            throw new UserNotFoundException(INTERNAL_SERVER_ERROR);
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
        String userAuthorization = headers.get("authorization") == null ? headers.get("Authorization").get(0) : headers.get(
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
        throw new UserNotFoundException(INTERNAL_SERVER_ERROR);
    }

    @Override
    public Set<String> getUserIdSetForService(MultiValueMap<String, String> headers, List<String> roles) {
        /*Set<String> users = new HashSet<>();
        users.add("asdfghjk-kjhgfds-dfghj-sdfghjk");
        return users;*/

//        Pattern rolePattern = Pattern.compile("^.*damage.*$");

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idamBaseURL + USER_FULL_NAME_ENDPOINT)
                .queryParam("query", "(roles:" + roles + ") AND lastModified:" + LAST_MODIFIED_TIME)
                .queryParam("size", USER_INFO_SIZE);
        LOG.debug("builder.toUriString() : {}", builder.toUriString());

        ResponseEntity<IdamUserListResponse> idamUserListResponseEntity = restTemplateIdam
                .exchange(
                        builder.toUriString(),
                        HttpMethod.GET,
                        getEntity(headers), IdamUserListResponse.class
                );

        if (idamUserListResponseEntity != null && idamUserListResponseEntity.getBody() != null) {
            IdamUserListResponse idamUserListResponse = idamUserListResponseEntity.getBody();

            if (idamUserListResponse != null && idamUserListResponse.getIdamUserInfoResponseList().size() > 0) {

                return idamUserListResponse.getIdamUserInfoResponseList().stream()
//                        .filter(pr -> pr.getRoles().stream().anyMatch(s -> rolePattern.matcher(s).matches()))
                        .map(IdamUserInfoResponse::getId)
                        .collect(Collectors.toSet());
            }
        }

        LOG.error("User id list not found for given service");
        throw new UserNotFoundException(INTERNAL_SERVER_ERROR);
    }
}
