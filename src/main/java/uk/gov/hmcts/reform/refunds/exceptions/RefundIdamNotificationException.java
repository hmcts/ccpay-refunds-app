package uk.gov.hmcts.reform.refunds.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class  RefundIdamNotificationException extends RuntimeException {

    private String server;

    private HttpStatusCode status;

    public static final long serialVersionUID = 333287436;

    public  RefundIdamNotificationException(String server, HttpStatusCode status, Throwable cause) {
        super(cause);
        this.server = server;
        this.status = status;
    }
}
