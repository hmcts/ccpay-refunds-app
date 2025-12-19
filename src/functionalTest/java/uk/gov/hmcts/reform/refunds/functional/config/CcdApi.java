package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.ProbateDraftCaseCreateRequest;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateCreateDraftTokenResponse;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateDraftCaseCreateResponse;

public interface CcdApi {

    @RequestLine("GET /caseworkers/{userId}/jurisdictions/PROBATE/case-types/GrantOfRepresentation/event-triggers/createDraft/token")
    @Headers({"Content-Type: application/json", "Authorization: {accessToken}", "ServiceAuthorization: {s2sToken}"})
    ProbateCreateDraftTokenResponse getProbateDraftCaseCreateToken(@Param("userId") String userId,
                                                                   @Param("accessToken") String accessToken,
                                                                   @Param("s2sToken") String s2sToken);

    @RequestLine("POST /case-types/GrantOfRepresentation/cases")
    @Headers({"Content-Type: application/json", "Authorization: {accessToken}", "ServiceAuthorization: {s2sToken}", "experimental: true"})
    ProbateDraftCaseCreateResponse createProbateDraftCase(@Param("accessToken") String accessToken,
                                                          @Param("s2sToken") String s2sToken,
                                                          ProbateDraftCaseCreateRequest probateDraftCaseCreateRequest);
}
