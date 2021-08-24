package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;

public interface IdamService {

    String getUserId(MultiValueMap<String, String> headers);

    String getUserFullName(MultiValueMap<String, String> headers, String uid);
}
