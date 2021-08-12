package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.requests.PaymentFeeDetails;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataFee;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberataResponse;
import uk.gov.hmcts.reform.refunds.exceptions.LiberataInvalidRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.LiberataServerException;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LiberataService {

    @Value("${liberata.api.url}")
    private String liberataApiUrl;

    @Qualifier("restTemplatePayment")
    @Autowired()
    private RestTemplate restTemplatePayment;

    private static final String LIBERATA_ENDPOINT = "/api/v3/refund";

    /**
     *
     * @param paymentDto
     * @param refund
     *  creates the request body for Liberata API
     * @return
     */
    public RefundLiberataRequest getLiberataRefundRequest(PaymentFeeDetails paymentDto, Refund refund){
        return RefundLiberataRequest.refundLiberataRequestWith()
            .refundReference(refund.getReference())
            .paymentReference(paymentDto.getReference())
            .dateCreated(refund.getDateCreated())
            .dateUpdated(refund.getDateUpdated())
            .refundReason(refund.getReason())
            .totalRefundAmount(refund.getAmount())
            .currency("GBP")
            .caseReference(paymentDto.getCaseReference())
            .ccdCaseNumber(paymentDto.getCcdCaseNumber())
            .accountNumber(paymentDto.getAccountNumber())
            .fees(getRefundRequestFees(paymentDto.getFees()))
            .build();
    }


    private List<RefundLiberataFee> getRefundRequestFees(List<FeeDto> fees) {
        return fees.stream().map(feeDto -> feeDtoMapToRefundLiberatFee(feeDto))
            .collect(Collectors.toList());
    }

    private RefundLiberataFee feeDtoMapToRefundLiberatFee(FeeDto feeDto){
        return RefundLiberataFee.refundLiberataFeeWith()
            .code(feeDto.getCode())
            .refundAmount(feeDto.getFeeAmount())
            .version(feeDto.getVersion())
            .build();
    }

    /**
     *
     * @param headers
     * @param refundLiberataRequest
     *
     * updates the liberata with given refund request.
     * @return
     */
    public ResponseEntity<RefundLiberataResponse> updateLiberataWithApprovedRefund(MultiValueMap<String, String> headers, RefundLiberataRequest refundLiberataRequest){
        // liberata function
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(liberataApiUrl + LIBERATA_ENDPOINT);
            return restTemplatePayment.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(refundLiberataRequest, headers), RefundLiberataResponse.class
            );
        } catch (HttpClientErrorException e){
            throw new LiberataInvalidRequestException("Invalid Request: Liberata", e);
        } catch ( Exception e){
            throw new LiberataServerException("Liberata Server Exception ", e);
        }
    }


}
