package uk.gov.hmcts.reform.refunds.controllers;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jmx.export.notification.UnableToSendNotificationException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.refunds.dtos.responses.ErrorResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotAllowedException;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.FeesNotFoundForRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.ForbiddenToApproveRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundFeeNotFoundInPaymentException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundReasonNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RetrospectiveRemissionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.UnequalRemissionAmountWithRefundRaisedException;
import uk.gov.hmcts.reform.refunds.exceptions.UserNotFoundException;

import java.util.LinkedList;
import java.util.List;



@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "unchecked", "rawtypes"})
@ControllerAdvice
public class ExceptionHandlers extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlers.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatus status, WebRequest request) {
        List<String> details = new LinkedList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            details.add(error.getDefaultMessage());
        }
        LOG.debug("Validation error", ex);
        ErrorResponse error = new ErrorResponse("Validation Failed", details);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({PaymentInvalidRequestException.class, RefundListEmptyException.class, ActionNotFoundException.class,
        ReconciliationProviderInvalidRequestException.class, InvalidRefundRequestException.class,
        InvalidRefundReviewRequestException.class, InvalidRefundNotificationResendRequestException.class})
    public ResponseEntity return400(Exception ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ForbiddenToApproveRefundException.class})
    public ResponseEntity return403(Exception ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }


    @ExceptionHandler({RefundNotFoundException.class, PaymentReferenceNotFoundException.class})
    public ResponseEntity return404(Exception ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({ActionNotAllowedException.class})
    public ResponseEntity return409(Exception ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler({PaymentServerException.class, ReconciliationProviderServerException.class, CheckDigitException.class,
        UserNotFoundException.class, RefundReasonNotFoundException.class,FeesNotFoundForRefundException.class,
        RefundFeeNotFoundInPaymentException.class,
        RetrospectiveRemissionNotFoundException.class, UnequalRemissionAmountWithRefundRaisedException.class})
    public ResponseEntity return500(Exception ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity return504(GatewayTimeoutException ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.GATEWAY_TIMEOUT);
    }


    @ExceptionHandler(UnableToSendNotificationException.class)
    public ResponseEntity return501(UnableToSendNotificationException ex) {
        LOG.error(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
