package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.refunds.dtos.PaymentDto;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SUBMITTED;

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
                            .dateCreated(Timestamp.from(Instant.now()))
                            .dateUpdated(Timestamp.from(Instant.now()))
                            .build();
        return refundsRepository.save(refund);
    }

    @Override
    public RefundResponse getRefundReference(MultiValueMap<String,String> headers, RefundRequest refundRequest) throws CheckDigitException {
        validateRefundRequest(headers, refundRequest);
        Refund refund = getRefundEntity(refundRequest);
        refundsRepository.save(refund);
        RefundResponse response = RefundResponse.buildRefundResponseWith()
                                    .refundReference(refund.getReference())
                                    .build();
        return response;
    }

    private void validateRefundRequest(MultiValueMap<String,String> headers, RefundRequest refundRequest){
        PaymentDto paymentDto = getPaymentForGiven(refundRequest.getPaymentReference(), headers);
        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(refundRequest.getPaymentReference());
        BigDecimal refundedHistoryAmount = refundsList.isPresent()?
                refundsList.get().stream().map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add):BigDecimal.ZERO;
        BigDecimal totalRefundedAmount = refundedHistoryAmount.add(refundRequest.getRefundAmount());

        if(!paymentDto.getStatus().equals("Success")){
            throw new InvalidRefundRequestException("Payment Status is not Success");
        }

        if(refundRequest.getRefundAmount()!=null &&
            isPaidAmountLessThanRefundRequestAmount(totalRefundedAmount, paymentDto.getAmount())){
            throw new InvalidRefundRequestException("Paid Amount is less than requested Refund Amount ");
        }

        if(!isRefundEligibilityFlagged()){
            throw new InvalidRefundRequestException("Refund Eligibility flag is unflagged");
        }

        if(refundRequest.getRefundAmount()==null){
            refundRequest.setRefundAmount(paymentDto.getAmount());
        }

    }

    private PaymentDto getPaymentForGiven(String paymentReference,MultiValueMap<String,String> headers) {
        Map<String, String> params = new HashMap<>();
        params.put("reference", paymentReference);
        ResponseEntity<PaymentDto> paymentDtoResponseEntity = restTemplatePayment.exchange(paymentURL + "/payments/{reference}", HttpMethod.GET, getHeadersEntity(headers), PaymentDto.class, params);
        return  paymentDtoResponseEntity.getBody();
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
//        inputHeaders.put("Authorization", headers.get("Authorization"));
//        inputHeaders.put("ServiceAuthorization", headers.get("ServiceAuthorization"));
        inputHeaders.put("Authorization", Arrays.asList("krishnakn00@gmail.com"));
        inputHeaders.put("ServiceAuthorization", Arrays.asList("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJjbWMiLCJleHAiOjE1MzMyMzc3NjN9.3iwg2cCa1_G9-TAMupqsQsIVBMWg9ORGir5xZyPhDabk09Ldk0-oQgDQq735TjDQzPI8AxL1PgjtOPDKeKyxfg[akiss@reformMgmtDevBastion02"));
        return new HttpEntity<>(inputHeaders);
    }

    private boolean isPaidAmountLessThanRefundRequestAmount(BigDecimal refundsAmount, BigDecimal paidAmount){
        // Actual logic is coming
        // Check for old refunds

        return paidAmount.compareTo(refundsAmount)<0;
    }

    private boolean isRefundEligibilityFlagged(){
        // Actual logic is coming
        return true;
    }

    private Refund getRefundEntity(RefundRequest refundRequest) throws CheckDigitException{
        return Refund.refundsWith()
                .amount(refundRequest.getRefundAmount())
                .paymentReference(refundRequest.getPaymentReference())
                .reason(RefundReason.getReasonObject(refundRequest.getRefundReason()))
                .refundStatus(SUBMITTED)
                .reference(referenceUtil.getNext("RF"))
                .build();
    }
}
