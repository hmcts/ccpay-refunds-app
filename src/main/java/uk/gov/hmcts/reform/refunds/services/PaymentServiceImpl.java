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
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentReferenceNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService{

    @Qualifier("restTemplatePayment")
    @Autowired()
    private RestTemplate restTemplatePayment;

    @Value("${payments.api.url}")
    private String paymentApiUrl;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    private static Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);


    @Override
    public PaymentGroupResponse fetchPaymentGroupResponse(MultiValueMap<String, String> headers, String paymentReference) {
        try{
            ResponseEntity<PaymentGroupResponse> paymentGroupResponse = fetchPaymentGroupDataFromPayhub(headers, paymentReference);
            if(paymentGroupResponse.getBody()!=null){
                checkPaymentReference(paymentGroupResponse.getBody(),  paymentReference);
                return paymentGroupResponse.getBody();
            }
            throw new PaymentReferenceNotFoundException("Payment Reference  not found");
        } catch (HttpClientErrorException e){
            if(e.getStatusCode().equals(HttpStatus.NOT_FOUND)){
                throw new PaymentReferenceNotFoundException("Payment Reference not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Request: Payhub", e);
        } catch ( Exception e){
            throw new PaymentServerException("Payment Server Exception", e);
        }
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
        List<String> authtoken = headers.get("Authorization");
        inputHeaders.put("Authorization",authtoken);
        inputHeaders.put("ServiceAuthorization", Arrays.asList(authTokenGenerator.generate()));
        logger.info("Auth {}", authtoken);
        logger.info(" Service Auth Authorization {}", Arrays.asList(authTokenGenerator.generate()));
        return new HttpEntity<>(inputHeaders);
    }

    private ResponseEntity<PaymentGroupResponse> fetchPaymentGroupDataFromPayhub(MultiValueMap<String,String> headers, String paymentReference){
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(paymentApiUrl).append("/payment-groups/fee-pay-apportion/").append(paymentReference).toString());
        logger.info("URI {}",builder.toUriString());
        return  restTemplatePayment
            .exchange(
                builder.toUriString(),
                HttpMethod.GET,
                getHeadersEntity(headers), PaymentGroupResponse.class);
    }

    private void checkPaymentReference(PaymentGroupResponse paymentGroupResponse, String paymentReference){
        List<PaymentResponse> paymentResponseList = paymentGroupResponse.getPayments()
            .stream().filter(paymentResponse1 -> paymentResponse1.getReference().equals(paymentReference))
            .collect(Collectors.toList());
        if(paymentResponseList.isEmpty()){
            throw new PaymentReferenceNotFoundException("Payment Reference  not found");
        }
    }


}
