package uk.gov.hmcts.reform.refunds.mappers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
public class ReconciliationProviderMapperTest {

    @Autowired
    private ReconciliationProviderMapper reconciliationProviderMapper;

    @Test
    public void testGetReconciliationProviderRequest() {
        ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper
            .getReconciliationProviderRequest(getPaymentGroupDto(), getRefund());
        assertEquals("PBAFUNC1234", reconciliationProviderRequest.getAccountNumber());
        assertEquals("RF-1628-5241-9956-2215", reconciliationProviderRequest.getRefundReference());
        assertEquals("RC-1628-5241-9956-2315", reconciliationProviderRequest.getPaymentReference());
        assertNotNull(reconciliationProviderRequest.getDateCreated());
        assertNotNull(reconciliationProviderRequest.getDateUpdated());
        assertEquals("RR0001", reconciliationProviderRequest.getRefundReason());
        assertEquals(BigDecimal.valueOf(100), reconciliationProviderRequest.getTotalRefundAmount());
        assertEquals("case-reference", reconciliationProviderRequest.getCaseReference());
        assertEquals("ccd-case-number", reconciliationProviderRequest.getCcdCaseNumber());
        assertEquals("FEE012", reconciliationProviderRequest.getFees().get(0).getCode());
        assertEquals("1", reconciliationProviderRequest.getFees().get(0).getVersion());
        assertEquals(BigDecimal.valueOf(100), reconciliationProviderRequest.getFees().get(0).getRefundAmount());
    }

    private PaymentGroupResponse getPaymentGroupDto() {
        return PaymentGroupResponse.paymentGroupDtoWith()
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

    private Refund getRefund() {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .reason("RR0001")
            .reference("RF-1628-5241-9956-2215")
            .paymentReference("RC-1628-5241-9956-2315")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .refundStatus(SENTFORAPPROVAL)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status(SENTFORAPPROVAL.getName())
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund Initiated")
                                               .build()))
            .build();
    }
}
