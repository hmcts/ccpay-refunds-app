package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundResubmitPayhubRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;

import java.util.Arrays;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    public static final String CONTENT_TYPE = "content-type";
    @Qualifier("restTemplatePayment")
    @Autowired()
    private RestTemplate restTemplatePayment;

    @Value("${payments.api.url}")
    private String paymentApiUrl;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    private static Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Override
    public PaymentGroupResponse fetchPaymentGroupResponse(MultiValueMap<String, String> headers,
                                                          String paymentReference) {
        try {
            ResponseEntity<PaymentGroupResponse> paymentGroupResponse =
                    fetchPaymentGroupDataFromPayhub(headers, paymentReference);
            logger.info("paymentGroupResponse != null: {}", paymentGroupResponse != null);
            if (paymentGroupResponse != null) {
                logger.info("paymentGroupResponse.getBody(): {}", paymentGroupResponse.getBody());
            }
            return paymentGroupResponse.getBody();
        } catch (HttpClientErrorException e) {
            logger.error(e.getMessage());
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                throw new PaymentReferenceNotFoundException("Payment Reference not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Request: Payhub", e);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new PaymentServerException("Payment Server Exception", e);
        }
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String, String> headers) {
        return new HttpEntity<>(getFormatedHeaders(headers));
    }

    private MultiValueMap<String, String> getFormatedHeaders(MultiValueMap<String, String> headers) {
        List<String> authtoken = headers.get("authorization");
        List<String> servauthtoken = Arrays.asList(authTokenGenerator.generate());
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put(CONTENT_TYPE, headers.get(CONTENT_TYPE));
        inputHeaders.put("Authorization", authtoken);
        inputHeaders.put("ServiceAuthorization", servauthtoken);
        return inputHeaders;
    }

    private ResponseEntity<PaymentGroupResponse> fetchPaymentGroupDataFromPayhub(MultiValueMap<String, String> headers,
                                                                                 String paymentReference) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                new StringBuilder(paymentApiUrl).append("/payment-groups/fee-pay-apportion/").append(paymentReference)
                        .toString());
        logger.info("URI {}", builder.toUriString());
        ResponseEntity<String> responseJson = restTemplatePayment
            .exchange(
                builder.toUriString(),
                HttpMethod.GET,
                getHeadersEntity(headers), String.class);
        if (responseJson != null) {
            logger.info("responseJson.getBody: {}", responseJson.getBody());
        }
        return restTemplatePayment
                .exchange(
                        builder.toUriString(),
                        HttpMethod.GET,
                        getHeadersEntity(headers), PaymentGroupResponse.class);
    }

    @Override
    public boolean updateRemissionAmountInPayhub(MultiValueMap<String, String> headers, String paymentReference,
                                                 RefundResubmitPayhubRequest refundResubmitPayhubRequest) {
        try {
            ResponseEntity<String> updateRemissionAmountPatchApi = updateRemissionAmountInPaymentApi(
                    headers,
                    paymentReference,
                    refundResubmitPayhubRequest
            );

            if (null != updateRemissionAmountPatchApi && updateRemissionAmountPatchApi.getStatusCode().is2xxSuccessful()) {
                return true;
            }

        } catch (HttpClientErrorException exception) {
            logger.error("Update Remission Client Exception",exception);
            if (exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                throw new PaymentReferenceNotFoundException("Payment reference not found", exception);
            }
            if (exception.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                throw new InvalidRefundRequestException(exception.getResponseBodyAsString(), exception);
            }
            throw new InvalidRefundRequestException("Invalid request. Please try again.", exception);
        } catch (Exception exception) {
            logger.error("Update Remission Server Exception",exception);
            throw new PaymentServerException("Payment server unavailable. Please try again.", exception);
        }
        return false;
    }


    private ResponseEntity<String> updateRemissionAmountInPaymentApi(MultiValueMap<String, String> headers,
                                                                     String paymentReference,
                                                                     RefundResubmitPayhubRequest refundResubmitPayhubRequest) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(paymentApiUrl).append(
                "/refund/resubmit/").append(paymentReference).toString());
        logger.info("URI {}", builder.toUriString());
        return restTemplatePayment
                .exchange(
                        builder.toUriString(),
                        HttpMethod.PATCH,
                        getHttpEntityForResubmitRefundsPatch(headers, refundResubmitPayhubRequest),
                        String.class
                );
    }

    private HttpEntity<RefundResubmitPayhubRequest> getHttpEntityForResubmitRefundsPatch(
            MultiValueMap<String, String> headers, RefundResubmitPayhubRequest request) {
        return new HttpEntity<>(request, getFormatedHeaders(headers));
    }

}
