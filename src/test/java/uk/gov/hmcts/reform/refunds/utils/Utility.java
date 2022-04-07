package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.model.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Supplier;

public class Utility {

    public static final String GET_REFUND_LIST_CCD_CASE_NUMBER = "1111-2222-3333-4444";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID1 = "1f2b7025-0f91-4737-92c6-b7a9baef14c6";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID2 = "userId2";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID3 = "userId3";
    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_STATUS = "2222-2222-3333-4444";
    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID = "2f2b7025-0f91-4737-92c6-b7a9baef14c6";
    public static final String GET_REFUND_LIST_SENDBACK_REFUND_STATUS = "3333-3333-3333-4444";
    public static final String GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID = "3f2b7025-0f91-4737-92c6-b7a9baef14c6";
    public static final Supplier<StatusHistory> STATUS_HISTORY_SUPPLIER = () -> StatusHistory.statusHistoryWith()
            .id(1)
            .status(RefundStatus.UPDATEREQUIRED.getName())
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .build();
    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber1 = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .contactDetails(ContactDetails.contactDetailsWith()
                    .addressLine("aaaa")
                    .city("bbbb")
                    .country("cccc")
                    .county("dddd")
                    .notificationType("LETTER")
                    .build())
            .build();
    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber2 = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .build();
    public static final Supplier<Refund> refundListSupplierForApprovedStatus = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.APPROVED)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .build();
    public static final Supplier<Refund> refundListSupplierForAcceptedStatus = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(900))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(900))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.ACCEPTED)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .build();
    public static final Supplier<Refund> refundListSupplierForSubmittedStatus = () -> Refund.refundsWith()
            .id(2)
            .amount(BigDecimal.valueOf(200))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_SUBMITTED_REFUND_STATUS)
            .createdBy(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
            .updatedBy(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
            .reference("RF-2222-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("Other")
            .paymentReference("RC-2222-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .build();
    public static final Supplier<Refund> refundListSupplierForSendBackStatus = () -> Refund.refundsWith()
            .id(3)
            .amount(BigDecimal.valueOf(300))
            .refundFees(Arrays.asList(
                    RefundFees.refundFeesWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(new BigDecimal(100))
                            .build()))
            .ccdCaseNumber(GET_REFUND_LIST_SENDBACK_REFUND_STATUS)
            .createdBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .updatedBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .reference("RF-3333-2234-1077-1123")
            .refundStatus(RefundStatus.UPDATEREQUIRED)
            .reason("Other")
            .paymentReference("RC-3333-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .statusHistories(Arrays.asList(STATUS_HISTORY_SUPPLIER.get()))
            .contactDetails(ContactDetails.contactDetailsWith()
                    .email("bb@bb.com")
                    .notificationType("EMAIL")
                    .build())
            .build();

    public static final Supplier<Refund> refundListSupplierForSendBackStatusforNullReason = () -> Refund.refundsWith()
            .id(3)
            .amount(BigDecimal.valueOf(300))
            .ccdCaseNumber(GET_REFUND_LIST_SENDBACK_REFUND_STATUS)
            .createdBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .updatedBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .reference("RF-3333-2234-1077-1123")
            .refundStatus(RefundStatus.UPDATEREQUIRED)
            .reason(null)
            .paymentReference("RC-3333-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .statusHistories(Arrays.asList(STATUS_HISTORY_SUPPLIER.get()))
            .contactDetails(ContactDetails.contactDetailsWith()
                    .email("bb@bb.com")
                    .notificationType("EMAIL")
                    .build())
            .build();
    public static final Supplier<PaymentResponse> PAYMENT_RESPONSE_SUPPLIER = () -> PaymentResponse.paymentResponseWith()
            .amount(BigDecimal.valueOf(100))
            .build();
    public static final Supplier<PaymentGroupResponse> PAYMENT_GROUP_RESPONSE = () -> PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("RF-3333-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .payments(Arrays.asList(PAYMENT_RESPONSE_SUPPLIER.get()))
            .build();
    public static final IdamUserIdResponse IDAM_USER_ID_RESPONSE =
            IdamUserIdResponse.idamUserIdResponseWith().uid("1").givenName("XX").familyName("YY").name("XX YY")
                    .roles(Arrays.asList("payments-refund-approver", "payments-refund")).sub("ZZ")
                    .build();
}
