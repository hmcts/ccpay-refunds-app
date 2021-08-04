package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.services.RefundReasonsService;

import java.util.List;


/**
 * Refund controller for backend rest api operations
 */
@RestController
@Api(tags = {"Refund Journey group"})
public class RefundController {

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
}
