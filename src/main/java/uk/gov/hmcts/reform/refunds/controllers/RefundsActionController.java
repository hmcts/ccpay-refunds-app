package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;

@RestController
@Api(tags = {"Refund Actions"})
public class RefundsActionController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsActionController.class);

    @Autowired
    private RefundReviewService refundReviewService;

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
