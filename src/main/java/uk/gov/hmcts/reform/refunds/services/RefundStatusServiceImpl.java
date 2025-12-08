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
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String ACCEPTED = "Accepted";
    private static final String EXPIRED = "Expired";
    private static final String LIBERATA_REASON = "Sent to Middle Office for Processing";

    private static final String SYSTEM_USER = "System user";

    private static final String LIBERATA_REJECT_UPDATE = "Refund approved by system";
    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusServiceImpl.class);

    private static final Pattern REF_PATTERN = Pattern.compile("\\bRF-\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b");

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

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
        final boolean isAClonedRefund = isAClonedRefund(refund);

        if (statusUpdateRequest.getStatus().getCode().equals(ACCEPTED)) {
            if (isAClonedRefund) {
                //ACECEPTED for the second time from Liberata this is going down the PAYIT journey
                refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);
            }
            // Get the original refund reference, it could the current one or the one from which it was cloned.
            final String originalRefundReference = getOriginalRefund(refund, isAClonedRefund);
            final String originalNoteForRejected = getOriginalNoteForRejected(refund);

            // no cloned.
            if (statusUpdateRequest.getReason() == null && originalNoteForRejected != null) {
                statusUpdateRequest.setReason(originalNoteForRejected);
            }
            if (isAClonedRefund) {
                Refund refundOriginal = refundsRepository.findByReferenceOrThrow(originalRefundReference);
                final String originalNoteForRejectedForOrginalRefund = getOriginalNoteForRejected(refundOriginal);
                statusUpdateRequest.setReason(originalNoteForRejectedForOrginalRefund);
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

    private boolean isAClonedRefund(Refund refund) {
        return statusHistoryRepository
            .findByRefundOrderByDateCreatedDesc(refund)
            .stream()
            .anyMatch(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()));
    }

    private String getOriginalNoteForRejected(Refund refund) {

        Optional<StatusHistory> statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund).stream()
            .filter(history -> RefundStatus.REJECTED.getName().equals(history.getStatus()))
            .findFirst();

        if (statusHistories.isPresent()) {
            return statusHistories.get().getNotes();
        } else {
            return null;
        }
    }

    private String getOriginalRefund(Refund refund, boolean isAClonedRefund) {
        if (isAClonedRefund) {
            // For cloned refunds, get the reference from the first REISSUED status history
            List<StatusHistory> statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund);
            Optional<StatusHistory> firstReissued = statusHistories
                .stream()
                .filter(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()))
                .findFirst();
            if (firstReissued.isPresent()) {
                return extractRefundReference(firstReissued.get().getNotes());
            } else {
                return null;
            }
        }
        // For non-cloned refunds, return the current refund reference
        return refund.getReference();
    }

    public String extractRefundReference(String input) {
        Matcher matcher = REF_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
