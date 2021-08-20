package uk.gov.hmcts.reform.refunds.service;


import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentAllocationResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.services.PaymentService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

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
    public void fetchPaymentDetailsReturnsValidResponse() throws ParseException{
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

        assertThat(paymentGroupResponse).usingRecursiveComparison().isEqualTo(getPaymentGroupDto());


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

