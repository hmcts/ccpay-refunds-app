package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.refunds.dtos.PaymentDto;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundsDomainServiceImpl implements RefundsDomainService {

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private ReferenceUtil referenceUtil;

    @Value("${payments.api.url}")
    private String paymentURL;


    @Autowired()
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;


    @Override
    public Refund saveRefund() {
        Refund refund = Refund.refundsWith()
                            .refundsId(UUID.randomUUID().toString())
                            .dateCreated(Timestamp.from(Instant.now()))
                            .dateUpdated(Timestamp.from(Instant.now()))
                            .build();
        return refundsRepository.save(refund);
    }

    @Override
    public RefundResponse getRefundReference(MultiValueMap<String,String> headers, RefundRequest refundRequest) throws CheckDigitException,InvalidRefundRequestException {
        validateRefundRequest(headers, refundRequest);
        RefundResponse response = RefundResponse.buildRefundResponseWith()
                                    .refundReference(referenceUtil.getNext("RF"))
                                    .build();
        return response;
    }

    private void validateRefundRequest(MultiValueMap<String,String> headers, RefundRequest refundRequest){
        PaymentDto paymentDto = getPaymentForGiven(refundRequest.getPaymentReference(), headers);
        if(!paymentDto.getStatus().equals("success")){
            throw new InvalidRefundRequestException("Payment Status is not Success");
        }

        if(refundRequest.getRefundAmount()!=null && isPaidAmountLessThanRefundRequestAmount(refundRequest.getRefundAmount(), paymentDto.getAmount())){
            throw new InvalidRefundRequestException("Paid Amount is less than requested Refund Amount ");
        }

        if(!isRefundEligibilityFlagged()){
            throw new InvalidRefundRequestException("Refund Eligibility flag is unflagged");
        }
    }

    private PaymentDto getPaymentForGiven(String paymentReference,MultiValueMap<String,String> headers){
        Map<String, String> params = new HashMap<>();
        params.put("reference", paymentReference);
        ResponseEntity<PaymentDto> paymentDtoResponseEntity = restTemplatePayment.exchange(paymentURL + "/payments/{reference}", HttpMethod.GET, getHeadersEntity(headers), PaymentDto.class, params);
        return  paymentDtoResponseEntity.getBody();
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
        inputHeaders.put("Authorization", headers.get("Authorization"));
        inputHeaders.put("ServiceAuthorization", headers.get("ServiceAuthorization"));
        HttpHeaders paymentRequestHeaders = new HttpHeaders(inputHeaders);
        return new HttpEntity<>(inputHeaders);
    }

    private boolean isPaidAmountLessThanRefundRequestAmount(BigDecimal refundsAmount, BigDecimal paidAmount){
        // Actual logic is coming
        return paidAmount.compareTo(refundsAmount)>0;
    }

    private boolean isRefundEligibilityFlagged(){
        // Actual logic is coming
        return true;
    }
}
