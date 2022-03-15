package uk.gov.hmcts.reform.refunds.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class  RefundIdamNotificationException extends RuntimeException {

    private String server;

    private HttpStatus status;

    public static final long serialVersionUID = 333287436;

    public  RefundIdamNotificationException(String server, HttpStatus status, Throwable cause) {
        super(cause);
        this.server = server;
        this.status = status;
    }
}
