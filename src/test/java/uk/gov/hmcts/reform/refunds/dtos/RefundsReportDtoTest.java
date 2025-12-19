package uk.gov.hmcts.reform.refunds.dtos;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundsReportDtoTest {

    @Test
    void testBuilderAndGetters() {
        Date now = new Date();
        BigDecimal amount = new BigDecimal("123.45");

        RefundsReportDto dto = RefundsReportDto.refundsReportDtoWith()
            .refundDateCreated(now)
            .refundDateUpdated(now)
            .amount(amount)
            .refundStatus("Approved")
            .refundReference("REF123")
            .paymentReference("PAY456")
            .ccdCaseNumber("1111-2222-3333-4444")
            .serviceType("civil")
            .notes("any notes")
            .build();

        assertEquals(now, dto.getRefundDateCreated());
        assertEquals(now, dto.getRefundDateUpdated());
        assertEquals(amount, dto.getAmount());
        assertEquals("Approved", dto.getRefundStatus());
        assertEquals("REF123", dto.getRefundReference());
        assertEquals("PAY456", dto.getPaymentReference());
        assertEquals("1111-2222-3333-4444", dto.getCcdCaseNumber());
        assertEquals("civil", dto.getServiceType());
        assertEquals("any notes", dto.getNotes());
    }

    @Test
    void testSettersAndNoArgsConstructor() {
        RefundsReportDto dto = new RefundsReportDto();
        Date date = new Date();
        BigDecimal amount = new BigDecimal("10.00");

        dto.setRefundDateCreated(date);
        dto.setRefundDateUpdated(date);
        dto.setAmount(amount);
        dto.setRefundStatus("Pending");
        dto.setRefundReference("REF999");
        dto.setPaymentReference("PAY999");
        dto.setCcdCaseNumber("CASE999");
        dto.setServiceType("family");
        dto.setNotes("Some notes");

        assertEquals(date, dto.getRefundDateCreated());
        assertEquals(date, dto.getRefundDateUpdated());
        assertEquals(amount, dto.getAmount());
        assertEquals("Pending", dto.getRefundStatus());
        assertEquals("REF999", dto.getRefundReference());
        assertEquals("PAY999", dto.getPaymentReference());
        assertEquals("CASE999", dto.getCcdCaseNumber());
        assertEquals("family", dto.getServiceType());
        assertEquals("Some notes", dto.getNotes());
    }
}
