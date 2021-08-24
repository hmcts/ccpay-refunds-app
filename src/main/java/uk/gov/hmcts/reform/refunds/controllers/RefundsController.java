package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

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

    @Autowired
    private RefundReasonsService refundReasonsService;
    @Autowired
    private RefundsService refundsService;

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

    @ApiOperation(value = "GET /refund ", notes = "Get refund list based on status")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "UnAuthorised"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 504, message = "Unable to retrieve service information")

    })
    @GetMapping("/refund")
    public ResponseEntity<RefundListDtoResponse> getRefundList(@RequestHeader(required = false) MultiValueMap<String, String> headers, @RequestParam String status
        , @RequestParam String ccdCaseNumber, @RequestParam String selfExclusive) {
        return new ResponseEntity<>(
            refundsService.getRefundList(
                status,
                headers,
                ccdCaseNumber,
                selfExclusive == null || selfExclusive.isBlank() ? "true" : selfExclusive // default true
            ),
            HttpStatus.OK
        );
    }

    //    @PatchMapping("/refund/reference/{reference}")
//    public HttpStatus reSubmitRefund(@RequestHeader(required = false) MultiValueMap<String, String> headers,
//                                     @PathVariable(value = "reference", required = true) String reference,
//                                     @Valid @RequestBody RefundRequest refundRequest) {
//
//
//        return refundsService.reSubmitRefund(headers, reference, refundRequest);
//    }

    @GetMapping("/refund/rejection-reasons")
    public ResponseEntity<List<String>> getRejectedReasons() {
        return ok().body(refundsService.getRejectedReasons());
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidRefundRequestException.class)
    public String return400(InvalidRefundRequestException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(UserNotFoundException.class)
    public String return500(UserNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(GatewayTimeoutException.class)
    public String return504(GatewayTimeoutException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(RefundListEmptyException.class)
    public String return400(RefundListEmptyException ex) {
        return ex.getMessage();
    }
}
