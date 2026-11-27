package uk.gov.hmcts.reform.refunds.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.exceptions.NotificationDeleteException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.NotificationService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundsDeletionControllerTest {

    private static final String CCD_CASE_NUMBER = "1747738470383168";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ContextStartListener contextStartListener;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void givenRefundsForCcdCase_whenDelete_thenNotificationsAndRefundsAreDeleted() throws Exception {
        Refund refund = buildRefund("RF-1756-8279-1771-7688");
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(List.of(refund)));

        mockMvc.perform(delete("/cases/{ccdCaseNumber}/refunds", CCD_CASE_NUMBER)
                .header("Authorization", "Bearer user-token")
                .header("ServiceAuthorization", "service-token"))
            .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(any(), eq("RF-1756-8279-1771-7688"));
        verify(refundsRepository).deleteAll(List.of(refund));
    }

    @Test
    void givenNoRefundsForCcdCase_whenDelete_thenReturnsNoContent() throws Exception {
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(Collections.emptyList()));

        mockMvc.perform(delete("/cases/{ccdCaseNumber}/refunds", CCD_CASE_NUMBER)
                .header("Authorization", "Bearer user-token")
                .header("ServiceAuthorization", "service-token"))
            .andExpect(status().isNoContent());

        verify(notificationService, never()).deleteNotification(any(), anyString());
        verify(refundsRepository, never()).deleteAll(any());
    }

    @Test
    void givenNotificationDeleteFails_whenDelete_thenReturnsInternalServerError() throws Exception {
        Refund refund = buildRefund("RF-1756-8279-1771-7688");
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(List.of(refund)));
        doThrow(new NotificationDeleteException("Notification delete failed"))
            .when(notificationService).deleteNotification(any(), eq("RF-1756-8279-1771-7688"));

        mockMvc.perform(delete("/cases/{ccdCaseNumber}/refunds", CCD_CASE_NUMBER)
                .header("Authorization", "Bearer user-token")
                .header("ServiceAuthorization", "service-token"))
            .andExpect(status().isInternalServerError());

        verify(refundsRepository, never()).deleteAll(any());
    }

    private Refund buildRefund(String reference) {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.TEN)
            .ccdCaseNumber(CCD_CASE_NUMBER)
            .reference(reference)
            .paymentReference("RC-1111-2222-3333-4444")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .build();
    }
}
