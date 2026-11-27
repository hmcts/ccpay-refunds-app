package uk.gov.hmcts.reform.refunds.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundsDeletionIntegrationTest {

    private static final String CCD_CASE_NUMBER = "1747738470383168";
    private static final String REFUND_REFERENCE = "RF-1756-8279-1771-7688";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean(name = "restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @MockBean
    private ContextStartListener contextStartListener;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
        when(authTokenGenerator.generate()).thenReturn("service-token");
    }

    @Test
    void deleteRefundsByCcdCaseNumber_shouldCallNotificationServiceThenDeleteRefunds() throws Exception {
        Refund refund = Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.TEN)
            .ccdCaseNumber(CCD_CASE_NUMBER)
            .reference(REFUND_REFERENCE)
            .paymentReference("RC-1111-2222-3333-4444")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .build();

        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(List.of(refund)));
        when(restTemplateNotify.exchange(
            eq("http://localhost:8185/notifications/v2/" + REFUND_REFERENCE),
            eq(HttpMethod.DELETE),
            any(HttpEntity.class),
            eq(Void.class)))
            .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(delete("/cases/{ccdCaseNumber}/refunds", CCD_CASE_NUMBER)
                .header("Authorization", "Bearer user-token")
                .header("ServiceAuthorization", "service-token"))
            .andExpect(status().isNoContent());

        verify(restTemplateNotify).exchange(
            eq("http://localhost:8185/notifications/v2/" + REFUND_REFERENCE),
            eq(HttpMethod.DELETE),
            any(HttpEntity.class),
            eq(Void.class));
        verify(refundsRepository).deleteAll(List.of(refund));
    }
}
