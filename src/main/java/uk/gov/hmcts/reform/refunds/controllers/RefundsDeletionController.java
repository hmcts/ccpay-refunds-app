package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.services.RefundsService;
import uk.gov.hmcts.reform.refunds.utils.LogUtils;

@RestController
@Tag(name = "Refunds Deletion")
public class RefundsDeletionController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsDeletionController.class);

    private final RefundsService refundsService;

    @Autowired
    public RefundsDeletionController(RefundsService refundsService) {
        this.refundsService = refundsService;
    }

    @Operation(summary = "DELETE /cases/{ccdCaseNumber}/refunds "
        + "Delete refunds and associated notifications for a CCD case number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Refunds deleted successfully, or no refunds found"),
        @ApiResponse(responseCode = "401", description = "IDAM User Authorization Failed"),
        @ApiResponse(responseCode = "403", description = "RPE Service Authentication Failed"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error. please try again later")
    })
    @DeleteMapping("/cases/{ccdCaseNumber}/refunds")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(rollbackFor = Exception.class)
    public void deleteRefundsByCcdCaseNumber(
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @PathVariable("ccdCaseNumber") String ccdCaseNumber) {
        String safeCcdCaseNumber = LogUtils.sanitize(ccdCaseNumber);
        LOG.info("Received request to delete refunds for CCD case number {}", safeCcdCaseNumber);
        try {
            refundsService.deleteRefundsByCcdCaseNumber(headers, ccdCaseNumber);
        } catch (Exception ex) {
            LOG.error("Error deleting refunds for CCD case number {}: {}",
                safeCcdCaseNumber, LogUtils.sanitize(ex.getMessage()), ex);
            throw ex;
        }
    }
}
