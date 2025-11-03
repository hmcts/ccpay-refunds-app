package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetailsResponse;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberataResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.IacService;
import uk.gov.hmcts.reform.refunds.services.IdamService;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;
import uk.gov.hmcts.reform.refunds.services.RefundStatusService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.RefundServiceRoleUtil;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@Tag(name = "Refund Journey group")
@SuppressWarnings({"PMD.AvoidUncheckedExceptionsInSignatures", "PMD.AvoidDuplicateLiterals", "PMD.ExcessiveImports"})
public class RefundsController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsController.class);

    private static final String REFUNDS_RELEASE = "refunds-release";

    private static final String IAC_SERVICE_NAME = "Immigration and Asylum Appeals";

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

    @Autowired
    private IdamService idamService;

    @Autowired
    private RefundServiceRoleUtil refundServiceRoleUtil;

    @Autowired
    private IacService iacService;

    @GetMapping("/refund/reasons")
    public ResponseEntity<List<RefundReason>> getRefundReason(@RequestHeader("Authorization") String authorization) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ok().body(refundReasonsService.findAll());
    }

    @Operation(summary = "POST /refund Submit Refund Request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "retrieved"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")

    })
    @PostMapping("/refund")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<RefundResponse> createRefund(@RequestHeader("Authorization") String authorization,
                                                       @RequestHeader(required = false) MultiValueMap<String, String> headers,
                                                       @Valid @RequestBody RefundRequest refundRequest) throws CheckDigitException,
                                                        InvalidRefundRequestException {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        IdamUserIdResponse idamUserIdResponse = idamService.getUserId(headers);
        refundServiceRoleUtil.validateRefundRoleWithServiceName(idamUserIdResponse.getRoles(), refundRequest.getServiceType());
        return new ResponseEntity<>(refundsService.initiateRefund(refundRequest, headers, idamUserIdResponse), HttpStatus.CREATED);
    }

    @Operation(summary = "GET /refund Get refund list based on status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "204", description = "Success, no Content"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "UnAuthorised"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "504", description = "Unable to retrieve service information")

    })
    @GetMapping("/refund")
    public ResponseEntity<RefundListDtoResponse> getRefundList(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String ccdCaseNumber,
        @RequestParam(required = false) String excludeCurrentUser) {


        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (StringUtils.isBlank(status) && StringUtils.isBlank(ccdCaseNumber)) {
            throw new RefundListEmptyException(
                "Please provide criteria to fetch refunds i.e. Refund status or ccd case number");
        }
        final RefundListDtoResponse response = refundsService.getRefundList(
            status,
            headers,
            ccdCaseNumber,
            excludeCurrentUser == null || excludeCurrentUser.isBlank() ? "false" : excludeCurrentUser
        );
        if (response.getRefundList().isEmpty()) {
            return ResponseEntity.noContent().build();  // HTTP 204 No Content
        } else {
            return ResponseEntity.ok(response);  // HTTP 200 OK with body
        }
    }

    @Operation(summary = "GET /refund/payment-failure-report Get payment failure report based on list of payment reference")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "UnAuthorised"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "503", description = "Service Unavailable"),
    })
    @GetMapping("/refund/payment-failure-report")
    public ResponseEntity getPaymentFailureReport(
        @RequestParam(required = false) List<String> paymentReferenceList) {

        if (featureToggler.getBooleanValue("payment-status-update-flag", false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (paymentReferenceList == null || paymentReferenceList.isEmpty()) {
            return new ResponseEntity<>("Please provide payment reference to retrieve payment failure report",HttpStatus.BAD_REQUEST);
        }
        LOG.info("Fetching the payment failure report based on given payment reference list");

        Optional<List<Refund>> paymentFailureList = refundsService.getPaymentFailureReport(paymentReferenceList);

        if (paymentFailureList.isPresent() && !paymentFailureList.get().isEmpty()) {
            LOG.info("Sending the payment failure report response");

            return new ResponseEntity<>(refundsService.getPaymentFailureDtoResponse(paymentFailureList.get()), HttpStatus.OK);
        } else {
            return new
                ResponseEntity<>(
                "Payment failure details for the given payment reference list is not found. Please provide valid Payment Reference",
                HttpStatus.NOT_FOUND
            );
        }

    }

    @Operation(summary = "Update refund status by refund reference Update refund status by refund reference")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "No content"),
        @ApiResponse(responseCode = "404", description = "Refund details not found")
    })
    @PatchMapping("/refund/{reference}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity updateRefundStatus(@RequestHeader(required = false) MultiValueMap<String, String> headers,
                                             @PathVariable("reference") String reference,
                                             @RequestBody @Valid RefundStatusUpdateRequest request) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE, false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return refundStatusService.updateRefundStatus(reference, request, headers);
    }

    @Operation(summary = "Update refund reason and amount by refund reference notes Update refund reason and amount by refund reference")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "No content"),
            @ApiResponse(responseCode = "404", description = "Refund details not found")
    })
    @PatchMapping("/refund/resubmit/{reference}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ResubmitRefundResponseDto> resubmitRefund(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable("reference") String reference,
            @RequestBody @Valid ResubmitRefundRequest request) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return new ResponseEntity<>(refundsService.resubmitRefund(reference, request, headers), HttpStatus.CREATED);
    }

    @GetMapping("/refund/rejection-reasons")
    public ResponseEntity<List<RejectionReasonResponse>> getRejectedReasons(@RequestHeader("Authorization") String authorization) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ok().body(refundsService.getRejectedReasons());
    }

    @GetMapping("/refund/{reference}/status-history")
    public ResponseEntity<StatusHistoryResponseDto> getStatusHistory(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable String reference) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.getStatusHistory(headers, reference), HttpStatus.OK);
    }

    @Operation(summary = "PATCH refund/{reference}/action/{reviewer-action} Review Refund Request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ok"),
        @ApiResponse(responseCode = "201", description = "Refund request reviewed successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "IDAM User Authorization Failed"),
        @ApiResponse(responseCode = "403", description = "RPE Service Authentication Failed"),
        @ApiResponse(responseCode = "404", description = "Refund Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error. please try again later")

    })
    @PatchMapping("/refund/{reference}/action/{reviewer-action}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> reviewRefund(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable String reference,
            @PathVariable("reviewer-action") ReviewerAction reviewerAction,
            @Valid @RequestBody RefundReviewRequest refundReviewRequest) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        LOG.info("Inside reviewer-action endpoint {}", reference);
        return refundReviewService.reviewRefund(headers, reference, reviewerAction.getEvent(), refundReviewRequest);
    }


    @GetMapping("/refund/{reference}/actions")
    public ResponseEntity<RefundEvent[]> retrieveActions(
        @RequestHeader("Authorization") String authorization,
        @PathVariable String reference) {
        if (featureToggler.getBooleanValue(REFUNDS_RELEASE,false)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return new ResponseEntity<>(refundsService.retrieveActions(reference), HttpStatus.OK);

    }

    @Operation(summary = "Delete Refund details by refund reference notes Delete refund details for supplied refund reference")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Refund deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found for the given reference")
    })
    @DeleteMapping("/refund/{reference}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRefund(@RequestHeader("Authorization") String authorization, @PathVariable String reference) {
        refundsService.deleteRefund(reference);
    }

    @Operation(summary = "PUT /refund/resend/notification/{reference} Resend Refund Notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ok"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "IDAM User Authorization Failed"),
        @ApiResponse(responseCode = "403", description = "RPE Service Authentication Failed"),
        @ApiResponse(responseCode = "404", description = "Refund Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error. please try again later")

    })
    @PutMapping("/refund/resend/notification/{reference}")
    public ResponseEntity<String> resendNotification(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @RequestBody ResendNotificationRequest resendNotificationRequest,
        @PathVariable String reference,
        @RequestParam NotificationType notificationType
    ) {
        LOG.info("Inside /refund/resend/notification/{reference}");
        resendNotificationRequest.setReference(reference);
        resendNotificationRequest.setNotificationType(notificationType);
        return refundNotificationService.resendRefundNotification(resendNotificationRequest,headers);
    }

    @Operation(summary = "Re-process failed notifications of type email and letter Re-process failed notifications of type email and letter")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Re-processed the failed email and letter notifications")
    })
    @PatchMapping("/jobs/refund-notification-update")
    @Transactional
    public void processFailedNotifcations() throws JsonProcessingException {
        LOG.info("Job refund notification email update started ...");
        refundNotificationService.processFailedNotificationsEmail();
        LOG.info("Job refund notification letter update started ...");
        refundNotificationService.processFailedNotificationsLetter();
    }

    @Operation(summary = "Get payments for Reconciliation for between dates Get list of payments."
        + "You can provide start date and end dates which can include times as well."
        + "Following are the supported date/time formats. These are yyyy-MM-dd, dd-MM-yyyy,"
        + "yyyy-MM-dd HH:mm:ss, dd-MM-yyyy HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss, dd-MM-yyyy'T'HH:mm:ss")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "404", description = "No refunds available for the given date range"),
        @ApiResponse(responseCode = "400", description = "Bad request"),
        @ApiResponse(responseCode = "413", description = "Date range exceeds the maximum supported by the system"),
        @ApiResponse(responseCode = "206", description = "Supplementary details partially retrieved"),
    })

    @GetMapping("/refunds")
    public ResponseEntity<RefundLiberataResponse> searchRefundReconciliation(@RequestParam(name = "start_date") Optional<String> startDateTimeString,
                                                                             @RequestParam(name = "end_date") Optional<String> endDateTimeString,
                                                                             @RequestParam(name = "refund_reference", required = false)
                                                                                      String refundReference) {

        List<RefundLiberata> refunds = refundsService
            .search(startDateTimeString, endDateTimeString,refundReference);

        Optional<RefundLiberata> iacRefundAny = refunds.stream()
            .filter(r -> r.getPayment().getServiceName().equalsIgnoreCase(IAC_SERVICE_NAME))
            .findAny();

        LOG.info("Is any IAC refund present: {}", iacRefundAny.isPresent());
        if (iacRefundAny.isPresent()) {
            ResponseEntity<SupplementaryDetailsResponse> responseEntitySupplementaryDetails =
                iacService.getIacSupplementaryDetails(refunds, IAC_SERVICE_NAME);

            if (responseEntitySupplementaryDetails.getStatusCode().equals(HttpStatus.OK)) {
                SupplementaryDetailsResponse supplementaryDetailsResponse = responseEntitySupplementaryDetails.getBody();
                LOG.info("Supplementary details response: {}", supplementaryDetailsResponse);
                refunds = iacService.updateIacSupplementaryDetails(refunds, supplementaryDetailsResponse);
            }
        }

        return new ResponseEntity<>(new RefundLiberataResponse(refunds), HttpStatus.OK);
    }


    @PostMapping("/refund/reissue-expired/{reference}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<RefundResponse> reissueExpired(@RequestHeader("Authorization") String authorization,
                                                         @RequestHeader(required = false) MultiValueMap<String, String> headers,
                                                         @PathVariable String reference) throws CheckDigitException,
        InvalidRefundRequestException {

        //  0 - Validate the reference format.
        //  1 - get the refund using the reference from the DB
        //  2 - build RefundRequest
        //  3 -  refundRequest.getServiceType();

        RefundRequest refundRequest = RefundRequest.refundRequestWith().serviceType("Damages").build();

        RefundResponse.buildRefundResponseWith().refundReference(reference).build();
        return new ResponseEntity<>(
            RefundResponse.buildRefundResponseWith().refundReference(reference).build(),
            HttpStatus.CREATED
        );
    }

}
