package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.ProbateDraftCaseCreateRequest;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.Data;
import uk.gov.hmcts.reform.refunds.functional.request.ccd.Event;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateCreateDraftTokenResponse;
import uk.gov.hmcts.reform.refunds.functional.response.ccd.ProbateDraftCaseCreateResponse;

@Service
public class CcdService {

    private static CcdApi ccdApi;
    private final TestConfigProperties testConfig;

    private static final Logger LOG = LoggerFactory.getLogger(IdamService.class);

    @Autowired
    public CcdService(TestConfigProperties testConfig) {
        this.testConfig = testConfig;
        ccdApi = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(CcdApi.class, testConfig.getCcdApiUrl());
    }

    public String createProbateDraftCase(String userId, String authToken, String s2sToken) {
        String  probateDraftCaseCreateToken= getProbateDraftCaseCreateToken(userId, authToken, s2sToken);
        ProbateDraftCaseCreateRequest probateDraftCaseCreateRequest = probateDraftCaseCreateRequest(probateDraftCaseCreateToken);
        LOG.info("Create Probate Draft Case Request : " + probateDraftCaseCreateRequest.toString());
        try {
            ProbateDraftCaseCreateResponse probateDraftCase = ccdApi.createProbateDraftCase(
                authToken,
                s2sToken,
                probateDraftCaseCreateRequest
            );
            return probateDraftCase.getId();

        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
    }

    public String getProbateDraftCaseCreateToken(String userId, String authToken, String s2sToken) {
        try {
            ProbateCreateDraftTokenResponse probateCreateDraftTokenResponse = ccdApi.getProbateDraftCaseCreateToken(
                userId,
                authToken,
                s2sToken
            );
            return probateCreateDraftTokenResponse.getToken();
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
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
