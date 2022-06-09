package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import javax.validation.Valid;

@RestController
@Api(tags = {"Refund Actions"})
public class RefundsActionController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsActionController.class);

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private RefundReviewService refundReviewService;

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
            @PathVariable(value = "reference") String reference,
            @PathVariable(value = "reviewer-action") ReviewerAction reviewerAction,
            @Valid @RequestBody RefundReviewRequest refundReviewRequest) {
        return refundReviewService.reviewRefund(headers, reference, reviewerAction.getEvent(), refundReviewRequest);
    }

    @ApiOperation(value = "GET /refund/{reference}/actions", notes = "Get refund actions")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Ok"),
            @ApiResponse(code = 400, message = "Bad Request"),
            @ApiResponse(code = 401, message = "IDAM User Authorization Failed"),
            @ApiResponse(code = 403, message = "RPE Service Authentication Failed"),
            @ApiResponse(code = 404, message = "Refund Not found"),
            @ApiResponse(code = 500, message = "Internal Server Error. please try again later")
    })
    @GetMapping("/refund/{reference}/actions")
    public ResponseEntity<RefundEvent[]> retrieveActions(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String reference) {
        return new ResponseEntity<>(refundsService.retrieveActions(reference), HttpStatus.OK);

    }

    @ApiOperation(value = "PATCH payment/{paymentReference}/action/cancel ", notes = "Cancel Refund Request")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "Bad Request"),
            @ApiResponse(code = 404, message = "Refund Not found"),
            @ApiResponse(code = 500, message = "Internal Server Error. please try again later")

    })
    @PatchMapping("/payment/{paymentReference}/action/cancel")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<String> cancelRefunds(
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @PathVariable String paymentReference) {
        LOG.info("Cancelling refunds with payment reference {}", paymentReference);
        return refundReviewService.cancelRefunds(headers, paymentReference);
    }
}
