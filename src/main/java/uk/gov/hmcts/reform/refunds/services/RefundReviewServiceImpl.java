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
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundLiberataRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
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


    @Qualifier("restTemplatePayment")
    @Autowired()
    private RestTemplate restTemplatePayment;

    @Autowired
    private IdamService idamService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RejectionReasonsRepository rejectionReasonsRepository;

    @Autowired
    private LiberataService liberataService;


    /**
     *
     * @param headers
     * @param reference
     * @param refundEvent
     * @param refundReviewRequest
     *
     * gets the refund review request validates it and updates the state in database.
     * @return
     */
    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference, RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        Refund refundForGivenReference = getRefundForReference(reference);
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
                RefundLiberataRequest refundLiberataRequest = liberataService.getLiberataRefundRequest(paymentDto, refundForGivenReference);
                ResponseEntity<RefundLiberataResponse> liberataResponse = liberataService.updateLiberataWithApprovedRefund(headers, refundLiberataRequest);
                if(liberataResponse.getStatusCode().is2xxSuccessful()){
                    updateRefundStatus(refundForGivenReference, refundEvent);
                }
        }

        if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){

            updateRefundStatus(refundForGivenReference, refundEvent);
        }

        return new ResponseEntity<>("Refund request reviewed successfully", HttpStatus.CREATED);
    }

    private Refund getRefundForReference(String reference){
        Optional<Refund> refund = refundsRepository.findByReference(reference);

        if(!refund.isPresent()){
            throw new RefundNotFoundException("Refunds not found for "+ reference);
        }

        if(refund.isPresent()&& !(refund.get().getRefundStatus().equals(SUBMITTED.getRefundStatus()))){
            throw new InvalidRefundReviewRequestException("Refund is not submitted"); // message required
        }

        return  refund.get();
    }

    private RejectionReason validateRefundRejectionReason(String reasonCode){
        Optional<RejectionReason> rejectionReasonObject = rejectionReasonsRepository.findByCode(reasonCode);
        if(!rejectionReasonObject.isPresent()){
            throw new InvalidRefundReviewRequestException("Reject reason is invalid");
        }
        return rejectionReasonObject.get();
    }


    /**
     * @param refund
     * @param refundEvent
     *
     * updates the refund status in database using state transition mechanism.
     * @return
     */
    private Refund updateRefundStatus(Refund refund, RefundEvent refundEvent) {
        RefundState updateStatusAfterAction = RefundState.valueOf(refund.getRefundStatus().getName().toUpperCase(Locale.getDefault()));
        // State transition logic
        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
        refund.setRefundStatus(newState.getRefundStatus());
        return refundsRepository.save(refund);
    }

    /**
     *
     * @param headers
     * @param paymentReference
     *
     * fetches Payment Group data from payment api for given payment reference
     * @return
     */
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
                throw new PaymentReferenceNotFoundException("Payment Reference "+ paymentReference+" not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Request: Payhub"+e.getMessage(), e);
        } catch ( Exception e){
           throw new PaymentServerException("Payment Server Exception", e);
        }
    }

    /**
     *
     * @param headers
     * @param paymentReference
     *
     * creates the payment details required for liberata request
     *
     * @return
     */
    private PaymentFeeDetails getPaymentData(MultiValueMap<String, String> headers, String paymentReference){
        ResponseEntity<PaymentGroupDto> paymentGroupDto = fetchDetailFromPayment(headers,paymentReference);
        List<PaymentDto> paymentDtoList = paymentGroupDto.getBody().getPayments()
                                            .stream().filter(paymentDto1 -> paymentDto1.getReference().equals(paymentReference))
                                            .collect(Collectors.toList());
        if(!paymentDtoList.isEmpty()){
            return PaymentFeeDetails.paymentFeeDetailsWith()
                .accountNumber(paymentDtoList.get(0).getAccountNumber())
                .caseReference(paymentDtoList.get(0).getCaseReference())
                .ccdCaseNumber(paymentDtoList.get(0).getCcdCaseNumber())
                .reference(paymentDtoList.get(0).getReference())
                .fees(paymentGroupDto.getBody().getFees())
                .build();
        }
        throw new PaymentReferenceNotFoundException("Payment Reference "+ paymentReference+ "not found");
    }

    private HttpEntity<String> getHeadersEntity(MultiValueMap<String,String> headers){
        MultiValueMap<String,String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("content-type",headers.get("content-type"));
        inputHeaders.put("Authorization", headers.get("Authorization"));
        inputHeaders.put("ServiceAuthorization", headers.get("ServiceAuthorization"));
        return new HttpEntity<>(inputHeaders);
    }

    /**
     *
     * @param refundEvent
     *
     * get the status for the given refund event
     * @return
     */
    private String getStatus(RefundEvent refundEvent){
        return  refundEvent.equals(RefundEvent.APPROVE)?"approved":refundEvent.equals(RefundEvent.REJECT)?"rejected":"sentback";
    }

    /**
     *
     * @param refundEvent
     * @param refundReviewRequest
     *
     * get the status notes to be added in status history for the given refund event
     *
     * @return
     */
    private String getStatusNotes(RefundEvent refundEvent,RefundReviewRequest refundReviewRequest){
        String notes;
        if(refundEvent.equals(RefundEvent.APPROVE)){
            notes =  "Refund Approved";
        }
        else if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){

            if(refundEvent.equals(refundEvent.REJECT)) {
                notes = getRejectNotes(refundReviewRequest);
            } else {
                if(refundReviewRequest.getReason()==null || refundReviewRequest.getReason().isEmpty()){
                    throw new InvalidRefundReviewRequestException("Enter reason for sendback");
                }else{
                    notes= refundReviewRequest.getReason();
                }
            }
        } else {
            notes="";
        }
        return notes;
    }


    private String getRejectNotes(RefundReviewRequest refundReviewRequest){
        if(refundReviewRequest.getCode()==null || refundReviewRequest.getCode().isEmpty()){
            throw new InvalidRefundReviewRequestException("Refund reject reason is required");
        }
        if(refundReviewRequest.getCode().equals("RR004")){
            if(refundReviewRequest!=null && ( refundReviewRequest.getReason()==null || refundReviewRequest.getReason().isEmpty())){
                throw new InvalidRefundReviewRequestException("Refund reject reason is required for others");
            }else{
                return refundReviewRequest.getReason();
            }
        }else{
            return validateRefundRejectionReason(refundReviewRequest.getCode()).getName();
        }
    }
}
