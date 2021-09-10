package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;

import java.util.List;
import java.util.Set;

public interface IdamService {

    String getUserId(MultiValueMap<String, String> headers);

    UserIdentityDataDto getUserIdentityData(MultiValueMap<String, String> headers, String uid);

    Set<String> getUserIdSetForRoles(MultiValueMap<String, String> headers, List<String> roles);
}
