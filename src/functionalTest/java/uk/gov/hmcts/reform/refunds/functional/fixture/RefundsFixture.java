package uk.gov.hmcts.reform.refunds.functional.fixture;

import com.google.common.collect.Lists;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.functional.request.ContactDetails;
import uk.gov.hmcts.reform.refunds.functional.request.CreditAccountPaymentRequest;
import uk.gov.hmcts.reform.refunds.functional.request.FeeDto;
import uk.gov.hmcts.reform.refunds.functional.request.PaymentRefundRequest;
import uk.gov.hmcts.reform.refunds.functional.request.ResubmitRefundRequest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public final class RefundsFixture {

    private RefundsFixture() {}

    public static CreditAccountPaymentRequest pbaPaymentRequestForProbate(final String amountString,
                                                                          final String service, final String pbaAccountNumber) {
        Random rand = new Random();
        String ccdCaseNumber = String.format((Locale)null, //don't want any thousand separators
                                             "%04d22%04d%04d%02d",
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(99));
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

    public static CreditAccountPaymentRequest pbaPaymentRequestForProbate(final String amountString,
                                                                          final String service,
                                                                          final String pbaAccountNumber,
                                                                          final String ccdCaseNumber) {
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

    public static CreditAccountPaymentRequest pbaPaymentRequest(final String service,
                                                                final String siteId,
                                                                final String pbaAccountNumber,
                                                                final String ccdCaseNumber,
                                                                final String feeAmount,
                                                                final String feeCode,
                                                                final String feeVersion) {
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(feeAmount))
            .description("FT PBA payment")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId(siteId)
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber(pbaAccountNumber)
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .code(feeCode)
                    .version(feeVersion)
                    .build())
            )
            .build();
    }

    public static PaymentRefundRequest refundRequest(final String refundReason,
                                                     final String paymentReference, final String refundAmount,
                                                     final String feeAmount, final int paymentId) {
        return PaymentRefundRequest
            .refundRequestWith().paymentReference(paymentReference)
            .refundReason(refundReason)
            .isOverPayment(false)
            .totalRefundAmount(new BigDecimal(refundAmount))
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .apportionAmount(BigDecimal.valueOf(0))
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .refundAmount(new BigDecimal(feeAmount))
                    .code("FEE0001")
                    .id(paymentId)
                    .version("1")
                    .updatedVolume(1)
                    .refundAmount(new BigDecimal("90.00"))
                    .build())
            )
            .contactDetails(ContactDetails.contactDetailsWith()
                                                           .addressLine("High Street 112")
                                                           .country("UK")
                                                           .county("Londonshire")
                                                           .city("London")
                                                           .postalCode("P1 1PO")
                                                           .email("test@HMCTS.NET")
                                                           .notificationType("EMAIL")
                                                           .build())
            .build();

    }

    public static PaymentRefundRequest refundRequest(final String refundReason,
                                                     final String paymentReference, final String refundAmount,
                                                     final String feeAmount, final String feeCode,
                                                     final String feeVersion, final int feeId) {
        return PaymentRefundRequest
            .refundRequestWith().paymentReference(paymentReference)
            .refundReason(refundReason)
            .isOverPayment(false)
            .totalRefundAmount(new BigDecimal(refundAmount))
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .apportionAmount(new BigDecimal(feeAmount))
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .code(feeCode)
                    .id(feeId)
                    .version(feeVersion)
                    .updatedVolume(1)
                    .refundAmount(new BigDecimal(refundAmount))
                    .build())
            )
            .contactDetails(ContactDetails.contactDetailsWith()
                .addressLine("High Street 112")
                .country("UK")
                .county("Londonshire")
                .city("London")
                .postalCode("P1 1PO")
                .email("test@HMCTS.NET")
                .notificationType("EMAIL")
                .build())
            .build();

    }

    public static PaymentRefundRequest refundRequestWithLetter(final String refundReason,
                                                     final String paymentReference, final String refundAmount,
                                                     final String feeAmount, final int paymentId) {
        return PaymentRefundRequest
            .refundRequestWith().paymentReference(paymentReference)
            .refundReason(refundReason)
            .isOverPayment(false)
            .totalRefundAmount(new BigDecimal(refundAmount))
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .apportionAmount(BigDecimal.valueOf(0))
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .refundAmount(new BigDecimal(feeAmount))
                    .code("FEE0001")
                    .id(paymentId)
                    .version("1")
                    .updatedVolume(1)
                    .refundAmount(new BigDecimal("90"))
                    .build())
            )
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("HA5 3XT")
                                .email("ranjeet.kumar@HMCTS.NET")
                                .notificationType("LETTER")
                                .build())
            .build();

    }

    public static PaymentRefundRequest refundRequest2Fees(final String refundReason, final String paymentReference,
                                                          final String refundAmount, final String feeAmount,
                                                          final int paymentId1, final int paymentId2) {
        return PaymentRefundRequest
            .refundRequestWith().paymentReference(paymentReference)
            .refundReason(refundReason)
            .isOverPayment(false)
            .totalRefundAmount(new BigDecimal(refundAmount))
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .apportionAmount(BigDecimal.valueOf(0))
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .refundAmount(new BigDecimal(feeAmount))
                    .code("FEE0001")
                    .id(paymentId1)
                    .version("1")
                    .updatedVolume(1)
                    .refundAmount(new BigDecimal("45"))
                    .build(),
                FeeDto.feeDtoWith()
                    .apportionAmount(BigDecimal.valueOf(0))
                    .calculatedAmount(new BigDecimal(feeAmount))
                    .refundAmount(new BigDecimal(feeAmount))
                    .code("FEE0001")
                    .id(paymentId2)
                    .version("1")
                    .updatedVolume(1)
                    .refundAmount(new BigDecimal("45"))
                    .build())
            )
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("P1 1PO")
                                .email("test@HMCTS.NET")
                                .notificationType("EMAIL")
                                .build())
            .build();

    }

    public static ResubmitRefundRequest resubmitRefundAllInput() {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(new BigDecimal("10.00"))
            .refundReason("RR002")
            .refundFees(Arrays.asList(
                RefundFeeDto.refundFeeRequestWith()
                    .feeId(1)
                    .code("FEE0001")
                    .version("1")
                    .volume(1)
                    .refundAmount(new BigDecimal("10.00"))
                    .build()))
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("HA5 3XT")
                                .email("testperson@somemail.com")
                                .notificationType("EMAIL")
                                .build())

            .build();
    }

    public static ResubmitRefundRequest resubmitRefundWithAmount() {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(new BigDecimal("85.00"))
            .build();
    }

    public static ResubmitRefundRequest resubmitRefundWithReason() {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .refundReason("RR011")
            .build();
    }

    public static ResubmitRefundRequest resubmitRefundWithContact() {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 115")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("HA5 3XT")
                                .email("testperson@somemail.com")
                                .notificationType("EMAIL")
                                .build())
            .build();
    }

    public static ResubmitRefundRequest resubmitRefundWithRetroRemissionReason() {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .refundReason("RR036")
            .build();
    }

    public static CreditAccountPaymentRequest pbaPaymentRequestForProbate2Fees(final String amountString,
                                                                          final String service, final String pbaAccountNumber) {
        Random rand = new Random();
        String ccdCaseNumber = String.format((Locale)null, //don't want any thousand separators
                                             "%04d22%04d%04d%02d",
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(10000),
                                             rand.nextInt(99));
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
                    .build(),
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }
}
