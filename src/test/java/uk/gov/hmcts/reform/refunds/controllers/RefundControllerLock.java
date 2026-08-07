package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.refunds.config.security.idam.IdamRepository;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RefundControllerLock {

    @MockBean
    private LaunchDarklyFeatureToggler featureToggle;

    @MockBean
    private ServiceAuthFilter serviceAuthFilter;

    @MockBean
    private IdamRepository idamRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    public RefundControllerLock() {
    }

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(serviceAuthFilter).doFilter(
            any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void lockedRefundReasonShouldThrowServiceUnavailable() throws Exception {

        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(get("/refund/reasons")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable()).andReturn();
    }

    @Test
    public void lockedCreateRefundShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(post("/refund")
                                                  .content(asJsonString(RefundRequest.refundRequestWith()
                                                                            .paymentReference("RC-1234-1234-1234-1234")
                                                                            .refundAmount(new BigDecimal(100))
                                                                            .paymentAmount(new BigDecimal(100))
                                                                            .refundReason("RR035-Other-Reason")
                                                                            .ccdCaseNumber("1111222233334444")
                                                                            .feeIds("1")
                                                                            .refundFees(Collections.singletonList(
                                                                                    RefundFeeDto.refundFeeRequestWith()
                                                                                            .feeId(1)
                                                                                            .code("RR001")
                                                                                            .version("1")
                                                                                            .volume(1)
                                                                                            .refundAmount(
                                                                                                    new BigDecimal(100))
                                                                                            .build()))
                                                                            .serviceType("cmc")
                                                                            .contactDetails(ContactDetails.contactDetailsWith().build())
                                                                            .build()))
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable()).andReturn();
    }

    @Test
    public void lockedGetRefundListShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(get("/refund")
                                                  .queryParam("status", "submitted")
                                                  .queryParam("ccdCaseNumber", "mock-ccd-num")
                                                  .queryParam("excludeCurrentUser", " ")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable()).andReturn();
    }

    @Test
    public void lockedUpdateRefundStatusShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        RefundStatusUpdateRequest refundStatusUpdateRequest = RefundStatusUpdateRequest.RefundRequestWith().status(
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
        mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234")
            .content(asJsonString(refundStatusUpdateRequest))
            .header("Authorization", "user")
            .header("ServiceAuthorization", "Services")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();
    }

    @Test
    public void lockedResubmitRefundShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(100))
            .refundFees(Collections.singletonList(
                    RefundFeeDto.refundFeeRequestWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .contactDetails(ContactDetails.contactDetailsWith().build())
            .refundReason("RR003").build();
        mockMvc.perform(patch(
            "/refund/resubmit/{reference}",
            "RF-1234-1234-1234-1234")
            .content(asJsonString(resubmitRefundRequest))
            .header("Authorization", "user")
            .header("ServiceAuthorization", "Services")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();
    }

    @Test
    public void lockedRejectionReasonsShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(get("/refund/rejection-reasons")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    }


    @Test
    public void lockedGetStatusHistoryShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(get("/refund/{reference}/status-history","RF-1233-1234-2341-1234")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    }

    @Test
    public void lockedReviewRefundShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
            .code("RR001")
            .build();
        mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1233-1234-2341-1234","REJECT")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .content(asJsonString(refundReviewRequest))
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();
    }

    @Test
    public void lockedReviewActionsShouldThrowServiceUnavailable() throws Exception {
        when(featureToggle.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(true);
        mockMvc.perform(get("/refund/{reference}/actions","RF-1233-1234-2341-1234")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();
    }

    @Test
    public void patchRefundWithoutAuthShouldReturn401() throws Exception {
        MockMvc securedMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RefundStatusUpdateRequest request = RefundStatusUpdateRequest.RefundRequestWith()
            .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
        securedMockMvc.perform(patch("/refund/{reference}", "RF-1234-1234-1234-1234")
                .content(asJsonString(request))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void patchRefundWithInsufficientRoleShouldReturn403() throws Exception {
        when(idamRepository.getUserInfo(anyString())).thenReturn(
            UserInfo.builder().uid("test-uid").roles(Collections.singletonList("payments")).build());
        MockMvc securedMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RefundStatusUpdateRequest request = RefundStatusUpdateRequest.RefundRequestWith()
            .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
        securedMockMvc.perform(patch("/refund/{reference}", "RF-1234-1234-1234-1234")
                .with(jwt().authorities(new SimpleGrantedAuthority("payments")))
                .content(asJsonString(request))
                .header("ServiceAuthorization", "Services")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void patchRefundWithRefundRoleShouldNotReturn401Or403() throws Exception {
        when(idamRepository.getUserInfo(anyString())).thenReturn(
            UserInfo.builder().uid("test-uid").roles(Collections.singletonList("payments-refund")).build());
        MockMvc securedMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RefundStatusUpdateRequest request = RefundStatusUpdateRequest.RefundRequestWith()
            .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
        int status = securedMockMvc.perform(patch("/refund/{reference}", "RF-1234-1234-1234-1234")
                .with(jwt().authorities(new SimpleGrantedAuthority("payments-refund")))
                .content(asJsonString(request))
                .header("ServiceAuthorization", "Services")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().getResponse().getStatus();
        Assertions.assertTrue(status != 401 && status != 403,
            "Expected status other than 401/403 but got " + status);
    }

    @Test
    public void patchRefundWithApproverRoleShouldNotReturn401Or403() throws Exception {
        when(idamRepository.getUserInfo(anyString())).thenReturn(
            UserInfo.builder().uid("test-uid").roles(Collections.singletonList("payments-refund-approver")).build());
        MockMvc securedMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RefundStatusUpdateRequest request = RefundStatusUpdateRequest.RefundRequestWith()
            .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
        int status = securedMockMvc.perform(patch("/refund/{reference}", "RF-1234-1234-1234-1234")
                .with(jwt().authorities(new SimpleGrantedAuthority("payments-refund-approver")))
                .content(asJsonString(request))
                .header("ServiceAuthorization", "Services")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().getResponse().getStatus();
        Assertions.assertTrue(status != 401 && status != 403,
            "Expected status other than 401/403 but got " + status);
    }

}
