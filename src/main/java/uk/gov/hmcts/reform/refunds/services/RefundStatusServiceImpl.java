package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.Notification;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;
import uk.gov.hmcts.reform.refunds.utils.StatusHistoryUtil;

import java.util.Arrays;
import java.util.Collections;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String ACCEPTED = "Accepted";
    private static final String EXPIRED = "Expired";
    private static final String LIBERATA_REASON = "Sent to Middle Office for Processing";

    private static final String SYSTEM_USER = "System user";

    private static final String LIBERATA_REJECT_UPDATE = "Refund approved by system";
    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusServiceImpl.class);

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private StatusHistoryUtil statusHistoryUtil;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private IdamService idamService;

    @Autowired
    RefundsUtil refundsUtil;

    private StatusHistory getStatusHistoryEntity(String uid, RefundStatus refundStatus, String reason) {
        return StatusHistory.statusHistoryWith()
            .createdBy(uid)
            .notes(reason)
            .status(refundStatus.getName())
            .build();
    }

    @Override
    public ResponseEntity updateRefundStatus(String reference, RefundStatusUpdateRequest statusUpdateRequest, MultiValueMap<String, String> headers) {
        LOG.info("statusUpdateRequest: {}", statusUpdateRequest);

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        final boolean isAClonedRefund = statusHistoryUtil.isAClonedRefund(refund);

        if (statusUpdateRequest.getStatus().getCode().equals(ACCEPTED)) {
            // Get the original refund reference, it could the current one or the one from which it was cloned.
            final String originalRefundReference = statusHistoryUtil.getOriginalRefundReference(refund);
            final String originalNoteForRejected = statusHistoryUtil.getOriginalNoteForRejected(refund);

            if (isAClonedRefund) {
                Refund refundOriginal = refundsRepository.findByReferenceOrThrow(originalRefundReference);
                final String originalNoteForRejectedForOrginalRefund = statusHistoryUtil.getOriginalNoteForRejected(refundOriginal);
                statusUpdateRequest.setReason(originalNoteForRejectedForOrginalRefund);
                refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);
            } else if (originalNoteForRejected != null
                && RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON.equalsIgnoreCase(originalNoteForRejected)) {
                refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);
                statusUpdateRequest.setReason(originalNoteForRejected);
            }

            refund.setRefundStatus(RefundStatus.ACCEPTED);
            refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                LIBERATA_NAME,
                RefundStatus.ACCEPTED,
                LIBERATA_REASON)
            ));

            IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
            String authorization =  "Bearer " + idamTokenResponse.getAccessToken();
            headers.put("authorization", Collections.singletonList(authorization));
            Notification notificationDetails = notificationService.getNotificationDetails(headers, originalRefundReference);

            if (notificationDetails == null) {
                LOG.error("Notification not found. Not able to send notification.");
            } else {
                ContactDetails newContact = ContactDetails.contactDetailsWith()
                    .notificationType(notificationDetails.getNotificationType())
                    .postalCode(notificationDetails.getContactDetails().getPostalCode())
                    .city(notificationDetails.getContactDetails().getCity())
                    .country(notificationDetails.getContactDetails().getCountry())
                    .county(notificationDetails.getContactDetails().getCounty())
                    .addressLine(notificationDetails.getContactDetails().getAddressLine())
                    .email(notificationDetails.getContactDetails().getEmail())
                    .build();
                refund.setContactDetails(newContact);
            }


            String templateId =  refundsUtil.getTemplate(refund, statusUpdateRequest.getReason());
            notificationService.updateNotification(headers, refund, null, templateId);

        } else if (statusUpdateRequest.getStatus().getCode().equals(EXPIRED)) {
            refund.setRefundStatus(RefundStatus.EXPIRED);
            refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                LIBERATA_NAME,
                RefundStatus.EXPIRED,
                statusUpdateRequest.getReason())
            ));
            refund.setUpdatedBy(LIBERATA_NAME);
        } else {
            refund.setRefundStatus(RefundStatus.REJECTED);
            refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                LIBERATA_NAME,
                RefundStatus.REJECTED,
                statusUpdateRequest.getReason())
            ));
            refund.setUpdatedBy(LIBERATA_NAME);

            if (null != statusUpdateRequest.getReason()
                && statusUpdateRequest.getReason().equalsIgnoreCase(
                RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)) {

                refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);

                refund.setRefundStatus(RefundStatus.APPROVED);
                refund.setUpdatedBy(SYSTEM_USER);
                Refund refundUpdated = refundsRepository.findByReferenceOrThrow(reference);
                refundUpdated.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    SYSTEM_USER,
                    RefundStatus.APPROVED,
                    LIBERATA_REJECT_UPDATE)
                ));
            }
        }
        return new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT);
    }
}
