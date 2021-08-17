package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.refunds.dto.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.services.RefundsDomainService;

import javax.validation.Valid;

@RestController
@Api(tags = {"Refund group"})
@SwaggerDefinition(tags = {@Tag(name = "RefundController", description = "Refund group REST API")})
public class RefundStatusUpdateController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusUpdateController.class);

    @Autowired
    private RefundsDomainService refundsService;

    @ApiOperation(value = "Update refund status by refund reference", notes = "Update refund status by refund reference")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "No content"),
        @ApiResponse(code = 404, message = "Refund details not found")
    })
    @PatchMapping("/refund/{reference}")
    @ResponseBody
    @Transactional
    public ResponseEntity updateRefundStatus(@PathVariable("reference") String reference,
                                             @RequestBody @Valid RefundStatusUpdateRequest request) {

        Refund refund = refundsService.retrieve(reference);
        if(refund != null)
        {
            refund.setRefundStatus(RefundStatus.refundsStatusWith().name(request.getStatus().getCode()).build());
            refund.setReason(RefundReason.refundsReasonWith().code(request.getReason()).build());
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
       else
        {
            throw new RefundNotFoundException("Refund reference not found");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RefundNotFoundException.class)
    public String notFound(RefundNotFoundException ex) {
        return ex.getMessage();
    }
}
