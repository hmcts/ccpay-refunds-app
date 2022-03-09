package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundResubmitPayhubRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentAllocationResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;
import uk.gov.hmcts.reform.refunds.services.PaymentService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
@SuppressWarnings({"PMD"})
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    private SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

    @Test
    void fetchPaymentDetailsReturnsValidResponse() throws ParseException {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())
        ));

        PaymentGroupResponse paymentGroupResponse =
            paymentService.fetchPaymentGroupResponse(headers, "RC-1628-5241-9956-2315");

        assertThat(paymentGroupResponse).usingRecursiveComparison().isEqualTo(getPaymentGroupDto());
    }

    @Test
    void fetchPaymentDetailsReturnsNotFoundException() throws Exception {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        PaymentGroupResponse paymentGroupResponse = PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(formatter.parse("7-Jun-2013"))
            .dateUpdated(formatter.parse("7-Jun-2013"))
            .payments(Collections.emptyList()).build();
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(PaymentReferenceNotFoundException.class, () -> {
            paymentService.fetchPaymentGroupResponse(headers, "RC-1628-5241-9956-2315");
        });
    }

    @Test
    void testUpdateRemissionAmountInPayhub() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest.refundResubmitRequestPayhubWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("RR003")
            .build();
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            String.class))).thenReturn(ResponseEntity.ok(null));
        Boolean updateResult = paymentService.updateRemissionAmountInPayhub(headers,
                                                                            "RC-1234-1234-1234-1234",
                                                                            refundResubmitPayhubRequest);
        assertTrue(updateResult);
    }

    @Test
    void testUpdateRemissionAmountInPayhub_ServerThrowsBadrequestException() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest.refundResubmitRequestPayhubWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("RR003")
            .build();
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            String.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        assertThrows(InvalidRefundRequestException.class, () -> {
            paymentService.updateRemissionAmountInPayhub(headers,
                                                         "RC-1234-1234-1234-1234",
                                                         refundResubmitPayhubRequest);
        });
    }

    @Test
    void testUpdateRemissionAmountInPayhub_ServerThrowsServerUnavailableException() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest.refundResubmitRequestPayhubWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("RR003")
            .build();
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            String.class))).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(PaymentServerException.class, () -> {
            paymentService.updateRemissionAmountInPayhub(headers,
                                                         "RC-1234-1234-1234-1234",
                                                         refundResubmitPayhubRequest);
        });
    }

    @Test
    void givenPaymentApiFailed_whenUpdateRemissionAmountInPayhub_thenFalseIsReceived() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest.refundResubmitRequestPayhubWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("RR003")
            .build();
        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            String.class))).thenReturn(ResponseEntity.notFound().build());
        Boolean updateResult = paymentService.updateRemissionAmountInPayhub(headers,
                                                                            "RC-1234-1234-1234-1234",
                                                                            refundResubmitPayhubRequest);
        assertFalse(updateResult);
    }

    @Test
    void givenNullRefundReason_whenUpdateRemissionAmountInPayhub_thenFalseIsReceived() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", "auth");
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        RefundResubmitPayhubRequest refundResubmitPayhubRequest = RefundResubmitPayhubRequest.refundResubmitRequestPayhubWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason(null)
            .build();
        Boolean updateResult = paymentService.updateRemissionAmountInPayhub(headers,
                                                                            "RC-1234-1234-1234-1234",
                                                                            refundResubmitPayhubRequest);
        assertFalse(updateResult);
    }

    @Test
    void fetchPaymentDetailsReturnsValidResponseForRefundReconciliation() throws ParseException {
        List<String> referenceList = new ArrayList<>();
        referenceList.add("RC-1628-5241-9956-2315");
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        List<PaymentDto> paymentDtos = new ArrayList<>();
        paymentDtos.add(getPayments());
        ResponseEntity<List<PaymentDto>> responseEntity = new ResponseEntity<>(paymentDtos, HttpStatus.OK);

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                   eq(new ParameterizedTypeReference<List<PaymentDto>>() {
                                   }))).thenReturn(responseEntity);

        List<PaymentDto> paymentDto =
            paymentService.fetchPaymentResponse(referenceList);

        assertThat(paymentDto).usingRecursiveComparison().isEqualTo(paymentDtos);
    }

    @Test
    void  fetchPaymentDetailsForRefundReconciliationReturnsNotFoundException() throws Exception {

        List<String> referenceList = new ArrayList<>();
        referenceList.add("RC-1628-5241-9956-2315");
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                          eq(new ParameterizedTypeReference<List<PaymentDto>>() {
                                          }))).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(PaymentReferenceNotFoundException.class, () -> {
            paymentService.fetchPaymentResponse(referenceList);
        });
    }

    @Test
    void  fetchPaymentDetailsForRefundReconciliationReturnsPaymentServerException() throws Exception {

        List<String> referenceList = new ArrayList<>();
        referenceList.add("RC-1628-5241-9956-2315");
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("ServiceAuthorization", "service-auth");
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                          eq(new ParameterizedTypeReference<List<PaymentDto>>() {
                                          }))).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(PaymentServerException.class, () -> {
            paymentService.fetchPaymentResponse(referenceList);
        });
    }

    private PaymentGroupResponse getPaymentGroupDto() throws ParseException {
        return PaymentGroupResponse.paymentGroupDtoWith()
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

    private PaymentDto getPayments() throws ParseException {

        PaymentDto payments = PaymentDto.payment2DtoWith()
            .accountNumber("123")
            .amount(BigDecimal.valueOf(100.00))
            .caseReference("test")
            .ccdCaseNumber("1111221383640739")
            .channel("bulk scan")
            .customerReference("123")
            .externalReference("test123")
            .giroSlipNo("tst")
            .method("cheque")
            .id("1")
            .reference("RC-1637-5115-4276-8564")
            .serviceName("Service")
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                                    .code("1")
                                    .jurisdiction1("test1")
                                    .jurisdiction2("test2")
                                    .version("1")
                                    .build()
                  )
            ).build();


        return payments;
    }

}

