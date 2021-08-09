package uk.gov.hmcts.reform.refunds.services;

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
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataFee;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberataResponse;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonsRepository;
import uk.gov.hmcts.reform.refunds.utils.ReviewerAction;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RefundReviewServiceImpl implements  RefundReviewService{

    @Value("${payments.api.url}")
    private String paymentApiUrl;

    @Value("${liberata.api.url}")
    private String liberataApiUrl;

    @Qualifier("restTemplatePayment")
    @Autowired()
    private RestTemplate restTemplatePayment;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RejectionReasonsRepository rejectionReasonsRepository;

    private static final String LIBERATA_ENDPOINT = "/api/v3/refund";


    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference, ReviewerAction reviewerAction, RefundReviewRequest refundReviewRequest) {
        validateRefundReviewRequest(reviewerAction, refundReviewRequest);

        Refund refundForGivenReference = getRefundForReference(reference);

        if(reviewerAction.equals(ReviewerAction.APPROVE)){
                ResponseEntity<PaymentDto> paymentDto = fetchDetailFromPayment(headers, refundForGivenReference.getPaymentReference());
                RefundLiberataRequest refundLiberataRequest = getLiberataRefundRequest(paymentDto.getBody(), refundForGivenReference);
                ResponseEntity<RefundLiberataResponse> liberataResponse = updateLiberataWithApprovedRefund(headers, refundLiberataRequest);
                if(liberataResponse.getStatusCode().is2xxSuccessful()){
                    updateRefundStatus(refundForGivenReference, reviewerAction);
                }
        }

        if(reviewerAction.equals(ReviewerAction.REJECT)||reviewerAction.equals(ReviewerAction.SENDBACK)){
            updateRefundStatus(refundForGivenReference, reviewerAction);
        }

        return new ResponseEntity<>("Refund request reviewed successfully", HttpStatus.CREATED);
    }

    private void  validateRefundReviewRequest( ReviewerAction reviewerAction, RefundReviewRequest refundReviewRequest) {
        if((reviewerAction.equals(ReviewerAction.REJECT)||reviewerAction.equals(ReviewerAction.SENDBACK)) &&
                    (refundReviewRequest.getCode()==null || refundReviewRequest.getCode().isEmpty())){
            throw new InvalidRefundRequestException("Refund Rejection Reason Required");
        }

        validateRefundRejectionReason(refundReviewRequest.getCode());
    }

    private Refund getRefundForReference(String reference){
        Optional<Refund> refund = refundsRepository.findByReference(reference);

        if(!refund.isPresent()){
            throw new RefundNotFoundException("Refunds not found for "+ reference);
        }

        if(refund.isPresent()&& !(refund.get().getRefundStatus().equals(RefundStatus.SUBMITTED))){
            throw new InvalidRefundRequestException("Refunds request is in "+ refund.get().getRefundStatus().getName());
        }

        return  refund.get();
    }

    private void validateRefundRejectionReason(String reasonCode){
        Optional<RejectionReason> rejectionReasonObject = rejectionReasonsRepository.findByCode(reasonCode);
        if(!rejectionReasonObject.isPresent()){
            throw new InvalidRefundRequestException("Rejection Reason is Invalid");
        }
    }

    private Refund updateRefundStatus(Refund refund, ReviewerAction reviewerAction) {
        RefundStatus updateStatusAfterAction =  reviewerAction.equals(ReviewerAction.APPROVE)?RefundStatus.SENT_TO_LIBERATA:
            reviewerAction.equals(ReviewerAction.REJECT)?RefundStatus.REJECTED:RefundStatus.SENTBACK;
        refund.setRefundStatus(updateStatusAfterAction);
        return refundsRepository.save(refund);
    }


    private ResponseEntity<PaymentDto> fetchDetailFromPayment(MultiValueMap<String, String> headers, String paymentReference) {
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(paymentApiUrl).append("/payments/").append(paymentReference).toString());
            return restTemplatePayment
                .exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    getHeadersEntity(headers), PaymentDto.class);
        } catch (HttpClientErrorException e){
            if(e.getStatusCode().equals(HttpStatus.NOT_FOUND)){
                throw new PaymentReferenceNotFoundException("Payment Reference "+ paymentReference+ "not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Payment Request "+e.getMessage(), e);
        } catch ( Exception e){
           throw new PaymentServerException("Payment Server Exception "+ e.getCause(), e);
        }
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
        inputHeaders.put("Authorization", headers.get("Authorization"));
        inputHeaders.put("ServiceAuthorization", headers.get("ServiceAuthorization"));
        return new HttpEntity<>(inputHeaders);
    }

    private RefundLiberataRequest getLiberataRefundRequest(PaymentDto paymentDto, Refund refund){
        return RefundLiberataRequest.refundLiberataRequestWith()
            .refundReference(refund.getReference())
            .paymentReference(paymentDto.getPaymentReference())
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

    private ResponseEntity<RefundLiberataResponse> updateLiberataWithApprovedRefund(MultiValueMap<String, String> headers, RefundLiberataRequest refundLiberataRequest){
        // liberata function
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(liberataApiUrl + LIBERATA_ENDPOINT);
            return restTemplatePayment.exchange(
                builder.toUriString(),
                HttpMethod.POST,
                new HttpEntity<>(refundLiberataRequest,headers), RefundLiberataResponse.class
            );
        } catch (HttpClientErrorException e){
            if(e.getStatusCode().is4xxClientError()){
                throw new LiberataInvalidRequestException("Liberata Invalid Request", e);
            }
            throw new LiberataInvalidRequestException("Liberata Invalid Request", e);
        } catch ( Exception e){
            throw new LiberataServerException("Liberata Server Exception "+e.getMessage(), e);
        }
    }

}
