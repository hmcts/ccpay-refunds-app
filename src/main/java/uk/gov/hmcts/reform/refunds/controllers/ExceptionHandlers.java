package uk.gov.hmcts.reform.refunds.controllers;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.refunds.dtos.responses.ErrorResponse;
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

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "unchecked", "rawtypes"})
@ControllerAdvice
public class ExceptionHandlers extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlers.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        List<String> details = new ArrayList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            details.add(error.getDefaultMessage());
        }
        LOG.debug("Validation error", ex);
        ErrorResponse error = new ErrorResponse("Validation Failed", details);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = {DataIntegrityViolationException.class})
    @ResponseStatus(code = CONFLICT)
    public void dataIntegrityViolationException(DataIntegrityViolationException e) {
        LOG.warn("Data integrity violation", e);
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
