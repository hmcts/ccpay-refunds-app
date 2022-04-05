package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;
import uk.gov.hmcts.reform.refunds.services.RefundStatusService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@Api(tags = {"Refund Journey group"})
@SuppressWarnings({"PMD.AvoidUncheckedExceptionsInSignatures", "PMD.AvoidDuplicateLiterals", "PMD.ExcessiveImports"})
public class RefundsController {

    @Autowired
    private RefundReasonsService refundReasonsService;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private RefundStatusService refundStatusService;

    @Autowired
    private RefundReviewService refundReviewService;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private RefundNotificationService refundNotificationService;

    @GetMapping("/refund/reasons")
    public ResponseEntity<List<RefundReason>> getRefundReason(@RequestHeader("Authorization") String authorization) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ok().body(refundReasonsService.findAll());
    }

    @ApiOperation(value = "POST /refund ", notes = "Submit Refund Request")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "retrieved"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 500, message = "Internal Server Error")

    })
    @PostMapping("/refund")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<RefundResponse> createRefund(@RequestHeader("Authorization") String authorization,
                                                       @RequestHeader(required = false) MultiValueMap<String, String> headers,
                                                       @Valid @RequestBody RefundRequest refundRequest) throws CheckDigitException,
                                                        InvalidRefundRequestException {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.initiateRefund(refundRequest, headers), HttpStatus.CREATED);
    }

    @ApiOperation(value = "GET /refund ", notes = "Get refund list based on status")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "UnAuthorised"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 504, message = "Unable to retrieve service information")

    })
    @GetMapping("/refund")
    public ResponseEntity<RefundListDtoResponse> getRefundList(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String ccdCaseNumber,
        @RequestParam(required = false) String excludeCurrentUser) {

        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (StringUtils.isBlank(status) && StringUtils.isBlank(ccdCaseNumber)) {
            throw new RefundListEmptyException(
                "Please provide criteria to fetch refunds i.e. Refund status or ccd case number");
        }

        return new ResponseEntity<>(
            refundsService.getRefundList(
                status,
                headers,
                ccdCaseNumber,
                excludeCurrentUser == null || excludeCurrentUser.isBlank() ? "false" : excludeCurrentUser
            ),
            HttpStatus.OK
        );
    }

    @ApiOperation(value = "Update refund status by refund reference", notes = "Update refund status by refund reference")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "No content"),
        @ApiResponse(code = 404, message = "Refund details not found")
    })
    @PatchMapping("/refund/{reference}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity updateRefundStatus(@RequestHeader(required = false) MultiValueMap<String, String> headers,
                                             @PathVariable("reference") String reference,
                                             @RequestBody @Valid RefundStatusUpdateRequest request) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return refundStatusService.updateRefundStatus(reference, request, headers);
    }

    @ApiOperation(value = "Update refund reason and amount by refund reference", notes = "Update refund reason and amount by refund reference")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "No content"),
            @ApiResponse(code = 404, message = "Refund details not found")
    })
    @PatchMapping("/refund/resubmit/{reference}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ResubmitRefundResponseDto> resubmitRefund(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable("reference") String reference,
            @RequestBody @Valid ResubmitRefundRequest request) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.resubmitRefund(reference, request, headers), HttpStatus.CREATED);
    }

    @GetMapping("/refund/rejection-reasons")
    public ResponseEntity<List<RejectionReasonResponse>> getRejectedReasons(@RequestHeader("Authorization") String authorization) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ok().body(refundsService.getRejectedReasons());
    }

    @GetMapping("/refund/{reference}/status-history")
    public ResponseEntity<StatusHistoryResponseDto> getStatusHistory(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable String reference) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.getStatusHistory(headers, reference), HttpStatus.OK);
    }

    @ApiOperation(value = "PATCH refund/{reference}/action/{reviewer-action} ", notes = "Review Refund Request")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Ok"),
        @ApiResponse(code = 201, message = "Refund request reviewed successfully"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "IDAM User Authorization Failed"),
        @ApiResponse(code = 403, message = "RPE Service Authentication Failed"),
        @ApiResponse(code = 404, message = "Refund Not found"),
        @ApiResponse(code = 500, message = "Internal Server Error. please try again later")

    })
    @PatchMapping("/refund/{reference}/action/{reviewer-action}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> reviewRefund(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable(value = "reference", required = true) String reference,
            @PathVariable(value = "reviewer-action", required = true) ReviewerAction reviewerAction,
            @Valid @RequestBody RefundReviewRequest refundReviewRequest) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return refundReviewService.reviewRefund(headers, reference, reviewerAction.getEvent(), refundReviewRequest);
    }


    @GetMapping("/refund/{reference}/actions")
    public ResponseEntity<RefundEvent[]> retrieveActions(
        @RequestHeader("Authorization") String authorization,
        @PathVariable String reference) {
        if (featureToggler.getBooleanValue("refunds-release",false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.retrieveActions(reference), HttpStatus.OK);

    }

    @ApiOperation(value = "PUT resend/notification/{reference} ", notes = "Resend Refund Notification")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Ok"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "IDAM User Authorization Failed"),
        @ApiResponse(code = 403, message = "RPE Service Authentication Failed"),
        @ApiResponse(code = 404, message = "Refund Not found"),
        @ApiResponse(code = 500, message = "Internal Server Error. please try again later")

    })
    @PutMapping("resend/notification/{reference}")
    public ResponseEntity<String> resendNotification(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @RequestBody ResendNotificationRequest resendNotificationRequest,
        @PathVariable String reference,
        @RequestParam NotificationType notificationType
    ) {
        resendNotificationRequest.setReference(reference);
        resendNotificationRequest.setNotificationType(notificationType);
        return refundNotificationService.resendRefundNotification(resendNotificationRequest,headers);
    }

    @ApiOperation(value = "Re-process failed notifications of type email and letter",
        notes = "Re-process failed notifications of type email and letter")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Re-processed the failed email and letter notifications")
    })
    @PatchMapping("/jobs/refund-notification-update")
    @Transactional
    public void processFailedNotifcations() throws JsonProcessingException {
        refundNotificationService.processFailedNotificationsEmail();
        refundNotificationService.processFailedNotificationsLetter();
    }

    @GetMapping("/refunds")
    public ResponseEntity<String> searchRefundReconciliation1(@RequestParam(name = "start_date") Optional<String> startDateTimeString,
                                                        @RequestParam(name = "end_date") Optional<String> endDateTimeString,
                                                        @RequestParam(name = "refund_reference", required = false) String refundReference
    ) {
        return new ResponseEntity<String>("test",HttpStatus.OK);
    }

}
