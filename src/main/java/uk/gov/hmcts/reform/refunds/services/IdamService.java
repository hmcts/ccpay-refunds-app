package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;

import java.util.List;
import java.util.Set;

public interface IdamService {

    IdamUserIdResponse getUserId(MultiValueMap<String, String> headers);

    UserIdentityDataDto getUserIdentityData(MultiValueMap<String, String> headers, String uid);

    List<UserIdentityDataDto> getUsersForRoles(MultiValueMap<String, String> headers, List<String> roles);
}
