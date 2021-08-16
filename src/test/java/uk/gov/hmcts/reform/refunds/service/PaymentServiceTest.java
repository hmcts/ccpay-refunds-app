package uk.gov.hmcts.reform.refunds.service;


import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.shaded.org.apache.commons.lang.builder.EqualsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.services.PaymentService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.*;
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

    SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);


    @Test
    public void testFetchPaymentDetails() throws ParseException{
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization","auth");
        headers.add("ServiceAuthorization","service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())
        ));

       PaymentGroupResponse paymentGroupResponse =
            paymentService.fetchPaymentGroupResponse(headers, "RC-1628-5241-9956-2315");


        assertEquals("payment-group-reference",paymentGroupResponse.getPaymentGroupReference());
        assertNotNull(paymentGroupResponse.getDateUpdated());
        assertNotNull(paymentGroupResponse.getDateCreated());
        assertNotNull(paymentGroupResponse.getPayments());
        assertEquals("RC-1628-5241-9956-2315",paymentGroupResponse.getPayments().get(0).getReference());
        assertEquals("case-reference",paymentGroupResponse.getPayments().get(0).getCaseReference());
        assertEquals("ccd-case-number",paymentGroupResponse.getPayments().get(0).getCcdCaseNumber());
        assertEquals("solicitors portal",paymentGroupResponse.getPayments().get(0).getChannel());
        assertEquals("payment by account",paymentGroupResponse.getPayments().get(0).getMethod());
        assertEquals("provider",paymentGroupResponse.getPayments().get(0).getExternalProvider());
        assertEquals("PBAFUNC1234",paymentGroupResponse.getPayments().get(0).getAccountNumber());
        assertEquals("org-name",paymentGroupResponse.getPayments().get(0).getOrganisationName());
        assertEquals("customer-reference",paymentGroupResponse.getPayments().get(0).getCustomerReference());
        assertEquals("success",paymentGroupResponse.getPayments().get(0).getStatus());
        assertEquals("divorce",paymentGroupResponse.getPayments().get(0).getServiceName());
        assertEquals("site-id",paymentGroupResponse.getPayments().get(0).getSiteId());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getPayments().get(0).getAmount());
        assertEquals("description",paymentGroupResponse.getPayments().get(0).getDescription());
        assertEquals(CurrencyCode.GBP,paymentGroupResponse.getPayments().get(0).getCurrency());
        assertEquals("solicitors portal",paymentGroupResponse.getPayments().get(0).getChannel());
        assertEquals("provider",paymentGroupResponse.getPayments().get(0).getExternalProvider());
        assertEquals("allocationStatus",paymentGroupResponse.getPayments().get(0).getPaymentAllocation().get(0).getAllocationStatus());
        assertEquals("allocationStatus",paymentGroupResponse.getPayments().get(0).getPaymentAllocation().get(0).getAllocationStatus());
        assertEquals("remission-reference",paymentGroupResponse.getRemissions().get(0).getRemissionReference());
        assertEquals("ben-ten",paymentGroupResponse.getRemissions().get(0).getBeneficiaryName());
        assertEquals("ccd-case-number",paymentGroupResponse.getRemissions().get(0).getCcdCaseNumber());
        assertEquals("case-reference",paymentGroupResponse.getRemissions().get(0).getCaseReference());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getFees().get(0).getCalculatedAmount());
        assertEquals("FEE012",paymentGroupResponse.getFees().get(0).getCode());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getFees().get(0).getNetAmount());
        assertEquals("1",paymentGroupResponse.getFees().get(0).getVersion());
        assertEquals(Integer.valueOf(1),paymentGroupResponse.getFees().get(0).getVolume());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getFees().get(0).getFeeAmount());
        assertEquals("ccd-case-number",paymentGroupResponse.getFees().get(0).getCcdCaseNumber());
        assertEquals("reference",paymentGroupResponse.getFees().get(0).getReference());
        assertEquals(Integer.valueOf(1),paymentGroupResponse.getFees().get(0).getId());
        assertEquals("memo-line",paymentGroupResponse.getFees().get(0).getMemoLine());
        assertEquals("natural-account-code",paymentGroupResponse.getFees().get(0).getNaturalAccountCode());
        assertEquals("description",paymentGroupResponse.getFees().get(0).getDescription());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getFees().get(0).getAllocatedAmount());
        assertEquals(BigDecimal.valueOf(100),paymentGroupResponse.getFees().get(0).getApportionAmount());
        assertNotNull(paymentGroupResponse.getFees().get(0).getDateCreated());
        assertNotNull(paymentGroupResponse.getFees().get(0).getDateUpdated());
        assertNotNull(paymentGroupResponse.getFees().get(0).getDateApportioned());
        assertEquals(BigDecimal.valueOf(0),paymentGroupResponse.getFees().get(0).getAmountDue());

    }

    private PaymentGroupResponse getPaymentGroupDto() throws ParseException {
        return  PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(formatter.parse("7-Jun-2013"))
            .dateUpdated(formatter.parse("7-Jun-2013"))
            .payments(Arrays.asList(
                PaymentResponse.paymentResponseWith()
                    .amount(BigDecimal.valueOf(100))
                    .description("description")
                    .reference("RC-1628-5241-9956-2315")
                    .dateCreated(formatter.parse("7-Jun-2013"))
                    .dateUpdated(formatter.parse("7-Jun-2013"))
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
                    .dateCreated(formatter.parse("7-Jun-2013"))
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
                    .ccdCaseNumber("ccd-case-number")
                    .reference("reference")
                    .memoLine("memo-line")
                    .id(1)
                    .naturalAccountCode("natural-account-code")
                    .description("description")
                    .allocatedAmount(BigDecimal.valueOf(100))
                    .apportionAmount(BigDecimal.valueOf(100))
                    .dateCreated(formatter.parse("7-Jun-2013"))
                    .dateUpdated(formatter.parse("7-Jun-2013"))
                    .dateApportioned(formatter.parse("7-Jun-2013"))
                    .amountDue(BigDecimal.valueOf(0))
                    .build()
            )).build();
    }

}

