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
import uk.gov.hmcts.reform.refunds.dtos.PaymentFeeDetailsDto;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.mappers.ReconciliationProviderMapper;
import uk.gov.hmcts.reform.refunds.mappers.RefundReviewMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
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
    private RefundReviewMapper refundReviewMapper;

    @Autowired
    private ReconciliationProviderService reconciliationProviderService;

    @Autowired
    private ReconciliationProviderMapper reconciliationProviderMapper;

    @Autowired
    private RefundsService refundsService;

    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference, RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        Refund refundForGivenReference = validatedAndGetRefundForGivenReference(reference);

        String userId = idamService.getUserId(headers);
        List<StatusHistory> statusHistories = new ArrayList<>(refundForGivenReference.getStatusHistories());
        refundForGivenReference.setUpdatedBy(userId);
        statusHistories.add(StatusHistory.statusHistoryWith()
                                .createdBy(userId)
                                .status(refundReviewMapper.getStatus(refundEvent))
                                .notes(refundReviewMapper.getStatusNotes(refundEvent, refundReviewRequest))
                                .build());
        refundForGivenReference.setStatusHistories(statusHistories);
        if(refundEvent.equals(RefundEvent.APPROVE)){
                PaymentFeeDetailsDto paymentDto = getPaymentData(headers, refundForGivenReference.getPaymentReference());
                ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper.getReconciliationProviderRequest(paymentDto, refundForGivenReference);
                ResponseEntity<ReconciliationProviderResponse> liberataResponse = reconciliationProviderService.updateReconciliationProviderWithApprovedRefund(headers,
                                                                                                                                                               reconciliationProviderRequest
                );
                if(liberataResponse.getStatusCode().is2xxSuccessful()){
                    updateRefundStatus(refundForGivenReference, refundEvent);
                }
        }

        if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){
            updateRefundStatus(refundForGivenReference, refundEvent);
        }
        return new ResponseEntity<>("Refund request reviewed successfully", HttpStatus.CREATED);
    }

    private Refund validatedAndGetRefundForGivenReference(String reference){
        Refund refund = refundsService.getRefundForReference(reference);

        if(!refund.getRefundStatus().equals(SUBMITTED.getRefundStatus())){
            throw new InvalidRefundReviewRequestException("Refund is not submitted");
        }
        return refund;
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

    private ResponseEntity<PaymentGroupResponse> fetchDetailFromPayment(MultiValueMap<String, String> headers, String paymentReference) {
        try{
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(paymentApiUrl).append("/payment-groups/fee-pay-apportion/").append(paymentReference).toString());
            return restTemplatePayment
                .exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    getHeadersEntity(headers), PaymentGroupResponse.class);

        } catch (HttpClientErrorException e){
            if(e.getStatusCode().equals(HttpStatus.NOT_FOUND)){
                throw new PaymentReferenceNotFoundException("Payment Reference "+ paymentReference+" not found", e);
            }
            throw new PaymentInvalidRequestException("Invalid Request: Payhub"+e.getMessage(), e);
        } catch ( Exception e){
           throw new PaymentServerException("Payment Server Exception", e);
        }
    }

    private PaymentFeeDetailsDto getPaymentData(MultiValueMap<String, String> headers, String paymentReference){
        ResponseEntity<PaymentGroupResponse> paymentGroupDto = fetchDetailFromPayment(headers, paymentReference);
        List<PaymentResponse> paymentResponseList = paymentGroupDto.getBody().getPayments()
                                            .stream().filter(paymentResponse1 -> paymentResponse1.getReference().equals(paymentReference))
                                            .collect(Collectors.toList());
        if(!paymentResponseList.isEmpty()){
            return PaymentFeeDetailsDto.paymentFeeDetailsWith()
                .accountNumber(paymentResponseList.get(0).getAccountNumber())
                .caseReference(paymentResponseList.get(0).getCaseReference())
                .ccdCaseNumber(paymentResponseList.get(0).getCcdCaseNumber())
                .paymentReference(paymentResponseList.get(0).getReference())
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


}
