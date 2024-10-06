package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.MissingSupplementaryInfo;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetails;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetailsResponse;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryInfo;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentRefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.services.IacServiceImpl;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IacServiceTest {

    @Mock
    private RestTemplate restTemplateIacSupplementaryInfo;

    @Mock
    private AuthTokenGenerator authTokenGenerator;

    private final String IAC_SERVICE_NAME = "Immigration and Asylum Chamber";

    private List<RefundLiberata> refundsDtos = null;

    private SupplementaryDetailsResponse supplementaryDetailsResponse = null;

    private RefundLiberata refundLiberata1, refundLiberata2 = null;


    @InjectMocks
    private IacServiceImpl iacService;

    @BeforeEach
    public void setUp() {

        PaymentRefundDto paymentRefundDto1 = PaymentRefundDto.paymentRefundDtoWith()
            .serviceName(IAC_SERVICE_NAME)
            .ccdCaseNumber("1111-2222-3333-4444")
            .caseReference(null)
            .build();

        PaymentRefundDto paymentRefundDto2 = PaymentRefundDto.paymentRefundDtoWith()
            .serviceName(IAC_SERVICE_NAME)
            .ccdCaseNumber("1111-2222-3333-5555")
            .caseReference(null)
            .build();

        refundLiberata1 = RefundLiberata.buildRefundLibarataWith()
            .fees(List.of())
            .payment(paymentRefundDto1)
            .build();

        refundLiberata2 = RefundLiberata.buildRefundLibarataWith()
            .fees(List.of())
            .payment(paymentRefundDto2)
            .build();

        supplementaryDetailsResponse = SupplementaryDetailsResponse.supplementaryDetailsResponseWith()
            .supplementaryInfo(Collections.singletonList(SupplementaryInfo.supplementaryInfoWith()
                                                             .ccdCaseNumber("1111-2222-3333-4444")
                                                             .supplementaryDetails(SupplementaryDetails.supplementaryDetailsWith()
                                                                                       .caseReferenceNumber("IAC/1234/REF")
                                                                                       .build())
                                                             .build()))
            .missingSupplementaryInfo(MissingSupplementaryInfo.missingSupplementaryInfoWith().build()).build();

        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetIacSupplementaryDetails_Success() {
        refundsDtos = List.of(refundLiberata1);
        ResponseEntity<SupplementaryDetailsResponse> responseEntity = new ResponseEntity<>(supplementaryDetailsResponse, HttpStatus.OK);
        when(restTemplateIacSupplementaryInfo.exchange(anyString(), any(), any(), eq(SupplementaryDetailsResponse.class)))
            .thenReturn(responseEntity);

        ResponseEntity<SupplementaryDetailsResponse> response = iacService.getIacSupplementaryDetails(refundsDtos, IAC_SERVICE_NAME);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IAC/1234/REF", response.getBody().getSupplementaryInfo().get(0).getSupplementaryDetails().getCaseReferenceNumber());
    }

    @Test
    public void testGetIacSupplementaryDetails_PartialContent() {
        refundsDtos = List.of(refundLiberata1);
        when(restTemplateIacSupplementaryInfo.exchange(anyString(), any(), any(), eq(SupplementaryDetailsResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        ResponseEntity<SupplementaryDetailsResponse> response = iacService.getIacSupplementaryDetails(refundsDtos, IAC_SERVICE_NAME);

        assertNotNull(response);
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    public void testUpdateIacSupplementaryDetails() {
        refundsDtos = List.of(refundLiberata1, refundLiberata2);
        ResponseEntity<SupplementaryDetailsResponse> responseEntity = new ResponseEntity<>(supplementaryDetailsResponse, HttpStatus.OK);
        when(restTemplateIacSupplementaryInfo.exchange(anyString(), any(), any(), eq(SupplementaryDetailsResponse.class)))
            .thenReturn(responseEntity);

        List<RefundLiberata> updatedRefunds = iacService.updateIacSupplementaryDetails(
            refundsDtos,
            supplementaryDetailsResponse
        );

        assertNotNull(updatedRefunds);
        assertEquals(refundsDtos.size(), updatedRefunds.size());
        assertEquals("1111-2222-3333-4444", updatedRefunds.get(0).getPayment().getCcdCaseNumber());
        assertEquals("IAC/1234/REF", updatedRefunds.get(0).getPayment().getCaseReference());

        // This case isn't in the supplementary details response, so it shouldn't be updated
        assertEquals("1111-2222-3333-5555", updatedRefunds.get(1).getPayment().getCcdCaseNumber());
        assertNull(updatedRefunds.get(1).getPayment().getCaseReference());
    }
}
