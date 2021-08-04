package uk.gov.hmcts.reform.refunds.controllers;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.services.RefundsDomainService;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Default endpoints per application.
 */
@RestController
@Api(tags = {"Refund group"})
@SwaggerDefinition(tags = {@Tag(name = "TestController", description = "Refund group REST API")})
public class RootController {

    /**
     * Root GET endpoint.
     *
     * <p>Azure application service has a hidden feature of making requests to root endpoint when
     * "Always On" is turned on.
     * This is the endpoint to deal with that and therefore silence the unnecessary 404s as a response code.
     *
     * @return Welcome message from the service.
     */

//    @Autowired
//    private RefundsService refundsService;
//
    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    @ApiOperation(value = "Get /refundstest ", notes = "Get refunds test")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "retrieved"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not found")
    })
    @GetMapping("/refundstest")
    public ResponseEntity<String> welcome() {
        boolean refundsEnabled = this.featureToggler.getBooleanValue("refund-test",false);
        return ok(refundsEnabled?"Welcome to refunds with feature enabled":"Welcome to refunds with feature false");
    }

//    @ApiOperation(value = "Get /refundstest ", notes = "Get refunds test")
//    @ApiResponses(value = {
//        @ApiResponse(code = 200, message = "retrieved"),
//        @ApiResponse(code = 403, message = "Forbidden"),
//        @ApiResponse(code = 404, message = "Not found")
//    })
//    @PostMapping("/refunds")
//    public ResponseEntity<Refund> storeRefunds( @RequestHeader("Authorization") String authorization) {
////        Refund refund= refundsDomainService.saveRefund();
//        return ok("dev in progress");
//    }
}
