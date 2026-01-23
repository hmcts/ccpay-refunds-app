package uk.gov.hmcts.reform.refunds.exceptions;

public class RefundReportException extends RuntimeException {
    public static final long serialVersionUID = 343297436;

    public RefundReportException(String message) {
        super(message);
    }

}
