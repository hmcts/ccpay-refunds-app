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


                    String authorization =  "Bearer eyJ0eXAiOiJKV1QiLCJraWQiOiIxZXIwV1J3Z0lPVEFGb2pFNHJDL2ZiZUt1M0k9Ii"
                        + "wiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhcHByb3ZlcmFhdHRlc3QxQG1haWxuZXNpYS5jb20iLCJjdHMiOiJPQVVUSDJfU"
                        + "1RBVEVMRVNTX0dSQU5UIiwiYXV0aF9sZXZlbCI6MCwiYXVkaXRUcmFja2luZ0lkIjoiZTQwMmMxOGYtMDM1Yi00NjI4LTg"
                        + "0MDMtZGFiNGU4YTliZGFiLTI4ODY3NDgwOSIsImlzcyI6Imh0dHBzOi8vZm9yZ2Vyb2NrLWFtLnNlcnZpY2UuY29yZS1jb"
                        + "21wdXRlLWlkYW0tYWF0Mi5pbnRlcm5hbDo4NDQzL29wZW5hbS9vYXV0aDIvcmVhbG1zL3Jvb3QvcmVhbG1zL2htY3RzIiw"
                        + "idG9rZW5OYW1lIjoiYWNjZXNzX3Rva2VuIiwidG9rZW5fdHlwZSI6IkJlYXJlciIsImF1dGhHcmFudElkIjoiXzdkNkVYc"
                        + "0lBeWJRWGlWYXIxT1Q1RUptRlU0IiwiYXVkIjoicGF5YnViYmxlIiwibmJmIjoxNjcxNDgwNzk5LCJncmFudF90eXBlIjoi"
                        + "YXV0aG9yaXphdGlvbl9jb2RlIiwic2NvcGUiOlsib3BlbmlkIiwicHJvZmlsZSIsInJvbGVzIiwic2VhcmNoLXVzZXIiXS"
                        + "wiYXV0aF90aW1lIjoxNjcxNDgwNzk5LCJyZWFsbSI6Ii9obWN0cyIsImV4cCI6MTY3MTUwOTU5OSwiaWF0IjoxNjcxNDgw"
                        + "Nzk5LCJleHBpcmVzX2luIjoyODgwMCwianRpIjoiSVFYbWU0eU9qRUFkWnlJWWNFamZaM1FBbmdrIn0.wLIpoPJH4daAVp"
                        + "fLTCY1UDepxlJY_LTSABhkPeImfiRf_Dp_w5qD7h2sJTveh-jeeFK2o5z509E2NzcI-e5zYtKqHla4sxwEr34fg448Rkj"
                        + "GsvuIx9y4szwMfSLr8l4lZYcbdInn6y5IFiUoVRU9m8h0YaiI6bYGkgIbyiFHFpihM54-fqX1Rf2GFBw1Q9iJrzQUb4DJY"
                        + "NI_AmrpH8Oh4JkwVwuhvjdUbGVG8c99lHFiJnWWfvh_J_wZrDyvhN6OFI4_RiHzDHXPdZ2U7DOQzoasocq8p_SHAn0v"
                        + "5rlrShFYYXouh-zr1wlQf_lGJCR2EJcJ1n7ez4FDjaGnBgMFaA";
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
