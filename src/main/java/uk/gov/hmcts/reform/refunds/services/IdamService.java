package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;

public interface IdamService {

    String getUserId(MultiValueMap<String, String> headers);

    UserIdentityDataDto getUserIdentityData(MultiValueMap<String, String> headers, String uid);
}
