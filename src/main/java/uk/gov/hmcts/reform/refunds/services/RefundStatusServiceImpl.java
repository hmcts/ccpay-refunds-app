package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotAllowedException;
import uk.gov.hmcts.reform.refunds.exceptions.NotificationNotFoundException;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.Arrays;
import java.util.Collections;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String ACCEPTED = "Accepted";
    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusServiceImpl.class);

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private NotificationService notificationService;

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

        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        if (currentRefundState.getRefundStatus().equals(RefundStatus.APPROVED)) {
            if (statusUpdateRequest.getStatus().getCode().equals(ACCEPTED)) {
                refund.setRefundStatus(RefundStatus.ACCEPTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    LIBERATA_NAME,
                    RefundStatus.ACCEPTED,
                    "Approved by middle office")
                ));
            } else {
                refund.setRefundStatus(RefundStatus.REJECTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    LIBERATA_NAME,
                    RefundStatus.REJECTED,
                    statusUpdateRequest.getReason())
                ));
                refund.setReason(statusUpdateRequest.getReason());

                if (null != statusUpdateRequest.getReason()
                    && statusUpdateRequest.getReason().equalsIgnoreCase(
                        RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)) {
                    refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);

                    String authorization =  "Bearer eyJ0eXAiOiJKV1QiLCJraWQiOiJaNEJjalZnZnZ1NVpleEt6QkVFbE1TbTQzTHM9Iiwi"
                        + "YWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhcHByb3ZlcmFhdHRlc3QxQG1haWxuZXNpYS5jb20iLCJjdHMiOiJPQVVUSDJfU1"
                        + "RBVEVMRVNTX0dSQU5UIiwiYXV0aF9sZXZlbCI6MCwiYXVkaXRUcmFja2luZ0lkIjoiYzU5MjA1NjQtMGViNy00MmUwLThkN"
                        + "zAtNTVlOWQyODgxNjAyLTIwNzM5NDI1MCIsImlzcyI6Imh0dHBzOi8vZm9yZ2Vyb2NrLWFtLnNlcnZpY2UuY29yZS1jb21"
                        + "wdXRlLWlkYW0tZGVtby5pbnRlcm5hbDo4NDQzL29wZW5hbS9vYXV0aDIvcmVhbG1zL3Jvb3QvcmVhbG1zL2htY3RzIiwidG9"
                        + "rZW5OYW1lIjoiYWNjZXNzX3Rva2VuIiwidG9rZW5fdHlwZSI6IkJlYXJlciIsImF1dGhHcmFudElkIjoicEdKVGhScTRmWWx"
                        + "yY1U2WVVTY19keFFrLVE0IiwiYXVkIjoicGF5YnViYmxlIiwibmJmIjoxNjcxNTMzNTk3LCJncmFudF90eXBlIjoiYXV0aG9y"
                        + "aXphdGlvbl9jb2RlIiwic2NvcGUiOlsib3BlbmlkIiwicHJvZmlsZSIsInJvbGVzIiwic2VhcmNoLXVzZXIiXSwiYXV0aF90aW"
                        + "1lIjoxNjcxNTMzNTk3LCJyZWFsbSI6Ii9obWN0cyIsImV4cCI6MTY3MTU2MjM5NywiaWF0IjoxNjcxNTMzNTk3LCJleHBpcmVzX"
                        + "2luIjoyODgwMCwianRpIjoiSzFSajRjQkxMeG5McEZpVXh0RGlUQXV6VXMwIn0.E8bPcIjs7pyuTGnwxez5Cm7s30NIcO7YK7kVd"
                        + "GF8xCtkdwhplKt4tByB04gOH717YowoJkZ1ZuFI_A53Km9qfsOvr1n9yFnTliRdnds7RGtFsteh13YYflWasR7G_q__vBNvZ6SrlD4"
                        + "OkPtKU3MDdfdMfPIsQsoorySsaxqYiHxtjYuf0WB60u9uRDTuCr_D1ThhksDd3KN6JwE5htfP75NxVf9r11vWHHofD01ojwV"
                        + "pw2fMNxR8zy68Lb0NAMD8t9d5PUDY2Ipp8UXgb9nAvBcALOojSdW7aygeNEcpg2cu6FBee2DU0ILedbrV8SkvQ7aRHnojKIr2pCYKsautrA";

                    headers.put("authorization", Collections.singletonList(authorization));
                    LOG.info("idamTokenResponse headers {}", headers);

                    String notificationType = notificationService.getNotificationType(headers, refund.getReference());
                    LOG.info(" Refund Status Service Impl Notifition type {}", notificationType);
                    if (notificationType == null) {
                        throw new NotificationNotFoundException("Notification not found");
                    }
                    ContactDetails newContact = ContactDetails.contactDetailsWith()
                        .notificationType(notificationType)
                        .build();
                    refund.setContactDetails(newContact);
                    notificationService.updateNotification(headers,refund);
                }
            }
            refund.setUpdatedBy(LIBERATA_NAME);
        } else {
            throw new ActionNotAllowedException("Action not allowed to proceed");
        }
        return new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT);
    }

}
