package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.dto.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;
import uk.gov.hmcts.reform.refunds.services.RefundStatusService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import javax.validation.Valid;
import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Refund controller for backend rest api operations
 */
@RestController
@Api(tags = {"Refund Journey group"})
@SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
public class RefundsController {


    private static final Logger LOG = LoggerFactory.getLogger(RefundsController.class);
    @Autowired
    private RefundReasonsService refundReasonsService;
    @Autowired
    private RefundsService refundsService;
    @Autowired
    private RefundStatusService refundStatusService;


    @Autowired
    private RefundReviewService refundReviewService;

    /**
     * Api for returning list of Refund reasons
     *
     * @return List of Refund reasons
     */
    @GetMapping("/refund/reasons")
    public ResponseEntity<List<RefundReason>> getRefundReason() {
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
    public ResponseEntity<RefundResponse> createRefund(@RequestHeader(required = false) MultiValueMap<String, String> headers,
                                                       @Valid @RequestBody RefundRequest refundRequest) throws CheckDigitException, InvalidRefundRequestException {
        return new ResponseEntity<>(refundsService.initiateRefund(refundRequest, headers), HttpStatus.CREATED);
    }


//    @PatchMapping("/refund/reference/{reference}")
//    public HttpStatus reSubmitRefund(@RequestHeader(required = false) MultiValueMap<String, String> headers,
//                                     @PathVariable(value = "reference", required = true) String reference,
//                                     @Valid @RequestBody RefundRequest refundRequest) {
//
//
//        return refundsService.reSubmitRefund(headers, reference, refundRequest);
//    }

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
        return refundStatusService.updateRefundStatus(reference, request, headers);
    }

    @GetMapping("/refund/rejection-reasons")
    public ResponseEntity<List<String>> getRejectedReasons() {
        return ok().body(refundsService.getRejectedReasons());
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
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @PathVariable(value = "reference", required = true) String reference,
        @PathVariable(value = "reviewer-action", required = true) ReviewerAction reviewerAction,
        @Valid @RequestBody RefundReviewRequest refundReviewRequest) {
        return refundReviewService.reviewRefund(headers, reference, reviewerAction.getEvent(), refundReviewRequest);
    }


    @GetMapping("/refunds/{reference}/actions")
    public ResponseEntity<RefundEvent[]> retrieveActions(
        @PathVariable(value = "reference", required = true) String reference) {
        return new ResponseEntity<>(refundsService.retrieveActions(reference), HttpStatus.OK);

    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({PaymentInvalidRequestException.class, ActionNotFoundException.class, ReconciliationProviderInvalidRequestException.class, InvalidRefundRequestException.class, InvalidRefundReviewRequestException.class})
    public String return400(Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({RefundNotFoundException.class, PaymentReferenceNotFoundException.class})
    public String return404(Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler({PaymentServerException.class, ReconciliationProviderServerException.class, CheckDigitException.class, UserNotFoundException.class})
    public String return500(Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(GatewayTimeoutException.class)
    public String return504(GatewayTimeoutException ex) {
        return ex.getMessage();
    }

}
