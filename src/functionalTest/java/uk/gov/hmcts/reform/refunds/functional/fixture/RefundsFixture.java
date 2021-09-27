package uk.gov.hmcts.reform.refunds.fixture;

import com.google.common.collect.Lists;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.request.FeeDto;
import uk.gov.hmcts.reform.refunds.request.PaymentRefundRequest;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;

public final class RefundsFixture {

    private RefundsFixture() {}

    public static final CreditAccountPaymentRequest pbaPaymentRequestForProbate(final String amountString,
                                                                                final String service, final String pbaAccountNumber) {
        Random rand = new Random();
        String ccdCaseNumber = String.format((Locale)null, //don't want any thousand separators
                                             "%04d22%04d%04d%02d",
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(99));
        System.out.println("The Correct CCD Case Number : " + ccdCaseNumber);
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber(pbaAccountNumber)
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static final PaymentRefundRequest refundRequest(final String refundReason,
                                                           final String paymentReference) {
        return PaymentRefundRequest
            .refundRequestWith().paymentReference(paymentReference)
            .refundReason(refundReason).build();

    }
}
