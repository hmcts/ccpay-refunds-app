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
import uk.gov.hmcts.reform.refunds.dtos.requests.PaymentFeeDetails;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataFee;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberataResponse;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import java.util.*;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.state.RefundState.SUBMITTED;

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
    private IdamService idamService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RejectionReasonsRepository rejectionReasonsRepository;

    private static final String LIBERATA_ENDPOINT = "/api/v3/refund";


    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference, RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        Refund refundForGivenReference = getRefundForReference(reference);
        validateRefundReviewRequest(refundEvent, refundReviewRequest);
        String userId = idamService.getUserId(headers);
        List<StatusHistory> statusHistories = new ArrayList<>(refundForGivenReference.getStatusHistories());
        refundForGivenReference.setUpdatedBy(userId);
        statusHistories.add(StatusHistory.statusHistoryWith()
                                .createdBy(userId)
                                .status(getStatus(refundEvent))
                                .notes(getStatusNotes(refundEvent, refundReviewRequest))
                                .build());
        refundForGivenReference.setStatusHistories(statusHistories);
        if(refundEvent.equals(RefundEvent.APPROVE)){
                PaymentFeeDetails paymentDto = getPaymentData(headers, refundForGivenReference.getPaymentReference());
                RefundLiberataRequest refundLiberataRequest = getLiberataRefundRequest(paymentDto, refundForGivenReference);
                ResponseEntity<RefundLiberataResponse> liberataResponse = updateLiberataWithApprovedRefund(headers, refundLiberataRequest);
                if(liberataResponse.getStatusCode().is2xxSuccessful()){
                    updateRefundStatus(refundForGivenReference, refundEvent);
                }
        }

        if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){
            validateRefundRejectionReason(refundReviewRequest.getCode());
            updateRefundStatus(refundForGivenReference, refundEvent);
        }

        return new ResponseEntity<>("Refund request reviewed successfully", HttpStatus.CREATED);
    }

    private void  validateRefundReviewRequest( RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        if(Arrays.stream(SUBMITTED.nextValidEvents()).noneMatch(refundEvent1 -> refundEvent1.equals(refundEvent))){
            throw new InvalidRefundRequestException("Invalid Refund Review Action");
        }

        if((refundEvent.equals(refundEvent.REJECT)||refundEvent.equals(refundEvent.SENDBACK)) &&
                    (refundReviewRequest.getCode()==null || refundReviewRequest.getCode().isEmpty())){
            throw new InvalidRefundRequestException("Refund Rejection Reason Required");
        }
    }

    private Refund getRefundForReference(String reference){
        Optional<Refund> refund = refundsRepository.findByReference(reference);

        if(!refund.isPresent()){
            throw new RefundNotFoundException("Refunds not found for "+ reference);
        }

        if(refund.isPresent()&& !(refund.get().getRefundStatus().equals(SUBMITTED.getRefundStatus()))){
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


    /**
     * @param refund
     * @param refundEvent
     *
     * @return
     */
    private Refund updateRefundStatus(Refund refund, RefundEvent refundEvent) {
        RefundState updateStatusAfterAction = RefundState.valueOf(refund.getRefundStatus().getName().toUpperCase(Locale.getDefault()));
        // State transition logic
        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
        refund.setRefundStatus(newState.getRefundStatus());
        return refundsRepository.save(refund);
    }


    private ResponseEntity<PaymentGroupDto> fetchDetailFromPayment(MultiValueMap<String, String> headers, String paymentReference) {
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(paymentApiUrl).append("/payment-groups/fee-pay-apportion/").append(paymentReference).toString());
            return restTemplatePayment
                .exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    getHeadersEntity(headers), PaymentGroupDto.class);


        } catch (HttpClientErrorException e){
            if(e.getStatusCode().equals(HttpStatus.NOT_FOUND)){
                throw new PaymentReferenceNotFoundException("Payment Reference "+ paymentReference+ "not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Payment Request "+e.getMessage(), e);
        } catch ( Exception e){
           throw new PaymentServerException("Payment Server Exception", e);
        }
    }

    private PaymentFeeDetails getPaymentData(MultiValueMap<String, String> headers, String paymentReference){
        ResponseEntity<PaymentGroupDto> paymentGroupDto = fetchDetailFromPayment(headers,paymentReference);
        List<PaymentDto> paymentDtoList = paymentGroupDto.getBody().getPayments()
                                            .stream().filter(paymentDto1 -> paymentDto1.getReference().equals(paymentReference))
                                            .collect(Collectors.toList());
        return PaymentFeeDetails.paymentFeeDetailsWith()
                .accountNumber(paymentDtoList.get(0).getAccountNumber())
                .caseReference(paymentDtoList.get(0).getCaseReference())
                .ccdCaseNumber(paymentDtoList.get(0).getCcdCaseNumber())
                .reference(paymentDtoList.get(0).getReference())
                .fees(paymentGroupDto.getBody().getFees())
                .build();
    }


    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
        inputHeaders.put("Authorization", headers.get("Authorization"));
        inputHeaders.put("ServiceAuthorization", headers.get("ServiceAuthorization"));
        return new HttpEntity<>(inputHeaders);
    }

    private RefundLiberataRequest getLiberataRefundRequest(PaymentFeeDetails paymentDto, Refund refund){
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
            throw new LiberataServerException("Liberata Server Exception ", e);
        }
    }

    private String getStatus(RefundEvent refundEvent){
        return  refundEvent.equals(RefundEvent.APPROVE)?"approved":refundEvent.equals(RefundEvent.REJECT)?"rejected":"sentback";
    }


    private String getStatusNotes(RefundEvent refundEvent,RefundReviewRequest refundReviewRequest){
        String notes;
        if(refundEvent.equals(RefundEvent.APPROVE)){
            notes =  "Refund Approved";
        }
        else if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){
            notes = refundReviewRequest.getReason();
        }else {
            notes="";
        }
        return notes;
    }
}
