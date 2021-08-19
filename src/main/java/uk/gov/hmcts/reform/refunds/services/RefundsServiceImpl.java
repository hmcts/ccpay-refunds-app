package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SUBMITTED;

@Service
@SuppressWarnings("PMD.PreserveStackTrace")
public class RefundsServiceImpl implements RefundsService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsServiceImpl.class);

    private static final Pattern REASONPATTERN = Pattern.compile("(^RR004-[a-zA-Z]+)|(RR004$)");

    private static final Pattern STATUSPATTERN = Pattern.compile("[^rejected]");

    private static int reasonPrefixLength = 6;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private ReferenceUtil referenceUtil;

    @Autowired
    private IdamService idamService;

    @Autowired
    private RefundReasonRepository refundReasonRepository;

    @Autowired
    private RejectionReasonRepository rejectionReasonRepository;

    @Override
    public RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException {
        validateRefundRequest(refundRequest);
        String uid = idamService.getUserId(headers);
        Refund refund = initiateRefundEntity(refundRequest, uid);
        refundsRepository.save(refund);
        LOG.info("Refund saved");
        return RefundResponse.buildRefundResponseWith()
            .refundReference(refund.getReference())
            .build();
    }


    @Override
    public HttpStatus reSubmitRefund(MultiValueMap<String, String> headers, String refundReference, RefundRequest refundRequest) {
//        Optional<Refund> refund = refundsRepository.findByReference(refundReference);
//        if (refund.isPresent()) {

//            String status = refund.get().getRefundStatus().getName();
//            List<String> nextValidEvents = Arrays.asList(RefundState.valueOf(status).nextValidEvents()).stream().map(
//                refundEvent1 -> refundEvent1.toString()).collect(
//                Collectors.toList());

//            RefundEvent[] ve = RefundState.valueOf(status).nextValidEvents();

//            if (nextValidEvents.contains(RefundEvent.valueOf(status))) {
//              return new ResponseEntity("Invalid refund event entered next valid refund events is/are : " + nextValidEvents, HttpStatus.BAD_REQUEST);
//            }
//
//          request.setState(currentstate.nextState(currentEventFromRequest));

//          if(RefundState.valueOf(refund.get().getRefundStatus().getName()).equals())
//            refund.get().setPaymentReference(refundRequest.getPaymentReference());
//            refund.get().setReason(RefundReason.getReasonObject(refundRequest.getRefundReason()).get());
//            refund.get().setReason(RefundReasonCode.valueOf(refundRequest.getRefundReason().getCode()));
//            refund.get().setRefundStatus(SUBMITTED);

//        }
        return HttpStatus.ACCEPTED;

    }

    @Override
    public List<String> getRejectedReasons() {
        // Getting names from Rejection Reasons List object
        return rejectionReasonRepository.findAll().stream().map(r -> r.getName())
            .collect(Collectors.toList());
    }

    private void validateRefundRequest(RefundRequest refundRequest) {

//        if (isRefundEligibilityFlagged()) {
//            throw new InvalidRefundRequestException("Refund Eligibility flag is unflagged");
//        }

        Optional<List<Refund>> refundsList = refundsRepository.findByPaymentReference(refundRequest.getPaymentReference());
        long refundProcessingCount = refundsList.isPresent() ? refundsList.get().stream().map(refund -> STATUSPATTERN.matcher(
            refund.getRefundStatus().getName()).find()).count() : 0;

        if (refundProcessingCount > 0) {
            throw new InvalidRefundRequestException("Refund is already processed for this payment");
        }

        Boolean matcher = REASONPATTERN.matcher(refundRequest.getRefundReason()).find();
        if (matcher) {
            if (refundRequest.getRefundReason().length() > reasonPrefixLength) {
                refundRequest.setRefundReason(refundRequest.getRefundReason().substring(reasonPrefixLength));
            } else {
                throw new InvalidRefundRequestException("Invalid reason selected");
            }
        } else {
            RefundReason refundReason = refundReasonRepository.findByCodeOrThrow(refundRequest.getRefundReason());
            refundRequest.setRefundReason(refundReason.getCode());
        }

    }
//
//    private boolean isRefundEligibilityFlagged() {
//        // Actual logic is coming
//        return false;
//    }

    private Refund initiateRefundEntity(RefundRequest refundRequest, String uid) throws CheckDigitException {
        return Refund.refundsWith()
            .amount(refundRequest.getRefundAmount())
            .ccdCaseNumber(refundRequest.getCcdCaseNumber())
            .paymentReference(refundRequest.getPaymentReference())
            .reason(refundRequest.getRefundReason())
            .refundStatus(SUBMITTED)
            .reference(referenceUtil.getNext("RF"))
            .createdBy(uid)
            .updatedBy(uid)
            .statusHistories(
                Arrays.asList(StatusHistory.statusHistoryWith()
                                  .createdBy(uid)
                                  .notes("Refund initiated")
                                  .status(SUBMITTED.getName())
                                  .build()
                )
            )
            .build();
    }
}
