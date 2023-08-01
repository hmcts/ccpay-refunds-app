package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Refund Actions")
public class RefundsActionController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsActionController.class);

    @Autowired
    private RefundReviewService refundReviewService;

    @Operation(summary = "PATCH payment/{paymentReference}/action/cancel Cancel Refund Request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Refund Not found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error. please try again later")

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
