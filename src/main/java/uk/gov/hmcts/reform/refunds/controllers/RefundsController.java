package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

import javax.validation.Valid;
import java.util.List;


/**
 * Refund controller for backend rest api operations
 */
@RestController
@Api(tags = {"Refund Journey group"})
@SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
public class RefundsController {

    @Autowired
    private RefundsService refundsService;

    @Autowired
    RefundReasonsService refundReasonsService;

    /**
     * Api for returning list of Refund reasons
     *
     * @return List of Refund reasons
     */
    @GetMapping("/refund/reasons")
    public ResponseEntity<List<RefundReason>> getRefundReason() {
        return ResponseEntity.ok().body(refundReasonsService.findAll());
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
    public RefundResponse createRefund(@RequestHeader(required = false) MultiValueMap<String, String> headers,
                                             @Valid @RequestBody RefundRequest refundRequest) throws CheckDigitException, InvalidRefundRequestException {
        return refundsService.initiateRefund(refundRequest, headers);
    }


    @PatchMapping("/refund/reference/{reference}")
    public HttpStatus reSubmitRefund(@RequestHeader(required = false) MultiValueMap<String, String> headers,
                                     @PathVariable(value = "reference", required = true) String reference,
                                     @Valid @RequestBody RefundRequest refundRequest) {


        return refundsService.reSubmitRefund(headers, reference, refundRequest);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidRefundRequestException.class)
    public String return400(InvalidRefundRequestException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PaymentReferenceNotFoundException.class)
    public String return404(PaymentReferenceNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(CheckDigitException.class)
    public String return500(CheckDigitException ex) {
        return ex.getMessage();
    }


}
