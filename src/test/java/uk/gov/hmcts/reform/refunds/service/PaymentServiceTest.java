package uk.gov.hmcts.reform.refunds.service;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.services.PaymentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @Test
    public void testFetchPaymentDetails(){
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization","auth");
        headers.add("ServiceAuthorization","service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())
        ));

        ResponseEntity<PaymentGroupResponse> responseEntity =
            paymentService.fetchDetailFromPayment(headers,"RC-1628-5241-9956-2315");

        assertEquals("payment-group-reference",responseEntity.getBody().getPaymentGroupReference());
        assertNotNull(responseEntity.getBody().getDateUpdated());
        assertNotNull(responseEntity.getBody().getDateCreated());
        assertNotNull(responseEntity.getBody().getPayments());
        assertEquals("RC-1628-5241-9956-2315",responseEntity.getBody().getPayments().get(0).getReference());
        assertEquals("case-reference",responseEntity.getBody().getPayments().get(0).getCaseReference());
        assertEquals("ccd-case-number",responseEntity.getBody().getPayments().get(0).getCcdCaseNumber());
        assertEquals("solicitors portal",responseEntity.getBody().getPayments().get(0).getChannel());
        assertEquals("payment by account",responseEntity.getBody().getPayments().get(0).getMethod());
        assertEquals("provider",responseEntity.getBody().getPayments().get(0).getExternalProvider());
        assertEquals("PBAFUNC1234",responseEntity.getBody().getPayments().get(0).getAccountNumber());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getPayments().get(0).getAmount());
        assertEquals("description",responseEntity.getBody().getPayments().get(0).getDescription());
        assertEquals(CurrencyCode.GBP,responseEntity.getBody().getPayments().get(0).getCurrency());
        assertEquals("solicitors portal",responseEntity.getBody().getPayments().get(0).getChannel());
        assertEquals("provider",responseEntity.getBody().getPayments().get(0).getExternalProvider());
        assertEquals("allocationStatus",responseEntity.getBody().getPayments().get(0).getPaymentAllocation().get(0).getAllocationStatus());
        assertEquals("allocationStatus",responseEntity.getBody().getPayments().get(0).getPaymentAllocation().get(0).getAllocationStatus());
        assertEquals("remission-reference",responseEntity.getBody().getRemissions().get(0).getRemissionReference());
        assertEquals("ben-ten",responseEntity.getBody().getRemissions().get(0).getBeneficiaryName());
        assertEquals("ccd-case-number",responseEntity.getBody().getRemissions().get(0).getCcdCaseNumber());
        assertEquals("case-reference",responseEntity.getBody().getRemissions().get(0).getCaseReference());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getFees().get(0).getCalculatedAmount());
        assertEquals("FEE012",responseEntity.getBody().getFees().get(0).getCode());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getFees().get(0).getNetAmount());
        assertEquals("1",responseEntity.getBody().getFees().get(0).getVersion());
        assertEquals(Integer.valueOf(1),responseEntity.getBody().getFees().get(0).getVolume());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getFees().get(0).getFeeAmount());
        assertEquals("ccd-case-number",responseEntity.getBody().getFees().get(0).getCcdCaseNumber());
        assertEquals("reference",responseEntity.getBody().getFees().get(0).getReference());
        assertEquals(Integer.valueOf(1),responseEntity.getBody().getFees().get(0).getId());
        assertEquals("memo-line",responseEntity.getBody().getFees().get(0).getMemoLine());
        assertEquals("natural-account-code",responseEntity.getBody().getFees().get(0).getNaturalAccountCode());
        assertEquals("description",responseEntity.getBody().getFees().get(0).getDescription());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getFees().get(0).getAllocatedAmount());
        assertEquals(BigDecimal.valueOf(100),responseEntity.getBody().getFees().get(0).getApportionAmount());
        assertNotNull(responseEntity.getBody().getFees().get(0).getDateCreated());
        assertNotNull(responseEntity.getBody().getFees().get(0).getDateUpdated());
        assertNotNull(responseEntity.getBody().getFees().get(0).getDateApportioned());
        assertEquals(BigDecimal.valueOf(0),responseEntity.getBody().getFees().get(0).getAmountDue());





    }

    private PaymentGroupResponse getPaymentGroupDto(){
        return  PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(Date.from(Instant.now()))
            .dateUpdated(Date.from(Instant.now()))
            .payments(Arrays.asList(
                PaymentResponse.paymentResponseWith()
                    .amount(BigDecimal.valueOf(100))
                    .description("description")
                    .reference("RC-1628-5241-9956-2315")
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .currency(CurrencyCode.GBP)
                    .caseReference("case-reference")
                    .ccdCaseNumber("ccd-case-number")
                    .channel("solicitors portal")
                    .method("payment by account")
                    .externalProvider("provider")
                    .accountNumber("PBAFUNC1234")
                    .organisationName("org-name")
                    .customerReference("customer-reference")
                    .status("success")
                    .serviceName("divorce")
                    .siteId("site-id")
                    .paymentAllocation(Arrays.asList(
                        PaymentAllocationResponse.paymentAllocationDtoWith()
                            .allocationStatus("allocationStatus")
                            .build()
                    ))
                    .build()
            ))
            .remissions(Arrays.asList(
                RemissionResponse.remissionDtoWith()
                    .remissionReference("remission-reference")
                    .beneficiaryName("ben-ten")
                    .ccdCaseNumber("ccd-case-number")
                    .caseReference("case-reference")
                    .hwfReference("hwf-reference")
                    .hwfAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .feeCode("FEE012")
                    .build()
            ))
            .fees(Arrays.asList(
                PaymentFeeResponse.feeDtoWith()
                    .code("FEE012")
                    .feeAmount(BigDecimal.valueOf(100))
                    .calculatedAmount(BigDecimal.valueOf(100))
                    .netAmount(BigDecimal.valueOf(100))
                    .version("1")
                    .volume(1)
                    .feeAmount(BigDecimal.valueOf(100))
                    .ccdCaseNumber("ccd-case-number")
                    .reference("reference")
                    .memoLine("memo-line")
                    .id(1)
                    .naturalAccountCode("natural-account-code")
                    .description("description")
                    .allocatedAmount(BigDecimal.valueOf(100))
                    .apportionAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .dateApportioned(Date.from(Instant.now()))
                    .amountDue(BigDecimal.valueOf(0))
                    .build()
            )).build();
    }

}

