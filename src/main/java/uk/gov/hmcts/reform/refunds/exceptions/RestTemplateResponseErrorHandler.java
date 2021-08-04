package uk.gov.hmcts.reform.refunds.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;

import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;

@Component
@SuppressWarnings("PMD.EmptyIfStmt")
public class RestTemplateResponseErrorHandler
    implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse httpResponse)
        throws IOException {

        return httpResponse.getStatusCode().series() == CLIENT_ERROR || httpResponse.getStatusCode().series() == SERVER_ERROR;
    }

    @Override
    public void handleError(ClientHttpResponse httpResponse)
        throws IOException {

        if (httpResponse.getStatusCode().series() == SERVER_ERROR) {
            // handle SERVER_ERROR
        } else if (httpResponse.getStatusCode().series() == CLIENT_ERROR && httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new PaymentReferenceNotFoundException("Payment Reference Not Found");
        }
    }
}
