package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.Data;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.Event;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.ProbateDraftCaseCreateRequest;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateCreateDraftTokenResponse;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateDraftCaseCreateResponse;

@Service
public class CcdService {

    private static CcdApi ccdApi;
    private final TestConfigProperties testConfig;

    private static final Logger LOG = LoggerFactory.getLogger(CcdService.class);

    @Autowired
    public CcdService(TestConfigProperties testConfig) {
        this.testConfig = testConfig;
        ccdApi = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(CcdApi.class, testConfig.getCcdApiUrl());
    }

    public String createProbateDraftCase(String userId, String authToken, String s2sToken) {
        String probateDraftCaseCreateToken = getProbateDraftCaseCreateToken(userId, authToken, s2sToken);
        ProbateDraftCaseCreateRequest probateDraftCaseCreateRequest = probateDraftCaseCreateRequest(probateDraftCaseCreateToken);
        LOG.info("Create Probate Draft Case Request : {}", probateDraftCaseCreateRequest);

        ProbateDraftCaseCreateResponse probateDraftCase;
        try {
            probateDraftCase = ccdApi.createProbateDraftCase(
                authToken,
                s2sToken,
                probateDraftCaseCreateRequest
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to create Probate draft case in CCD", ex);
        }

        if (probateDraftCase == null || probateDraftCase.getId() == null) {
            throw new IllegalStateException("CCD create Probate draft case response did not contain a case id");
        }

        return probateDraftCase.getId();
    }

    public String getProbateDraftCaseCreateToken(String userId, String authToken, String s2sToken) {
        ProbateCreateDraftTokenResponse probateCreateDraftTokenResponse;
        try {
            probateCreateDraftTokenResponse = ccdApi.getProbateDraftCaseCreateToken(
                userId,
                authToken,
                s2sToken
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to get Probate draft case create token from CCD", ex);
        }

        if (probateCreateDraftTokenResponse == null || probateCreateDraftTokenResponse.getToken() == null) {
            throw new IllegalStateException("CCD Probate draft case create token response did not contain a token");
        }

        return probateCreateDraftTokenResponse.getToken();
    }

    public ProbateDraftCaseCreateRequest probateDraftCaseCreateRequest(String createEventToken) {
        return ProbateDraftCaseCreateRequest.builder()
            .data(Data.builder().build())
            .event(
                Event.builder().build()
            )
            .eventToken(createEventToken)
            .build();
    }
}
