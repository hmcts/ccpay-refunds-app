package uk.gov.hmcts.reform.refunds.exceptions;

import java.io.Serializable;

public class InvalidRefundReviewRequestException extends RuntimeException implements Serializable {

    public static final long serialVersionUID = 313287432;

    public InvalidRefundReviewRequestException(String message) {
        super(message);
    }

}
