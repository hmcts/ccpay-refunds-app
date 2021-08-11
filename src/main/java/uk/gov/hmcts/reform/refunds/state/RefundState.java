package uk.gov.hmcts.reform.refunds.state;

import uk.gov.hmcts.reform.refunds.model.RefundStatus;


import static uk.gov.hmcts.reform.refunds.state.RefundEvent.ACCEPT;
import static uk.gov.hmcts.reform.refunds.state.RefundEvent.APPROVE;
import static uk.gov.hmcts.reform.refunds.state.RefundEvent.CANCEL;
import static uk.gov.hmcts.reform.refunds.state.RefundEvent.REJECT;
import static uk.gov.hmcts.reform.refunds.state.RefundEvent.SENDBACK;
import static uk.gov.hmcts.reform.refunds.state.RefundEvent.SUBMIT;

@SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
public enum RefundState {

    SUBMITTED {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{APPROVE, REJECT, SENDBACK};
        }

        @Override
        public RefundState nextState(RefundEvent event) {
            switch (event) {
                case APPROVE:
                    return SENT_TO_LIBERATA;
                case REJECT:
                    return REJECTED;
                case SENDBACK:
                    return NEEDMOREINFO;
                default:
                    return this;
            }
        }

        @Override
        public RefundStatus getRefundStatus(){
            return RefundStatus.SUBMITTED;
        }
    },
    NEEDMOREINFO {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{SUBMIT, CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case SUBMIT:
                    return SUBMITTED;
                case CANCEL:
                    return REJECTED;
                default:
                    return this;
            }
        }

        @Override
        public RefundStatus getRefundStatus(){
            return RefundStatus.SENTBACK;
        }
    },
    SENT_TO_LIBERATA {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{ACCEPT, CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case ACCEPT:
                    return ACCEPTED;
                case CANCEL:
                    return REJECTED;
                default:
                    return this;

            }
        }

        @Override
        public RefundStatus getRefundStatus(){
            return RefundStatus.SENT_TO_LIBERATA;
        }
    },
    ACCEPTED {
        @Override
        public RefundEvent[] nextValidEvents() {
            return RefundEvent.values();
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {
            return this;
        }

        @Override
        public RefundStatus getRefundStatus(){
            return RefundStatus.ACCEPTED;
        }
    },
    REJECTED {
        @Override
        public RefundEvent[] nextValidEvents() {
            return RefundEvent.values();
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {
            return this;
        }

        @Override
        public RefundStatus getRefundStatus(){
            return RefundStatus.REJECTED;
        }
    };

    public abstract RefundEvent[] nextValidEvents();

    public abstract RefundState nextState(RefundEvent refundEvent);

    public abstract RefundStatus getRefundStatus();
}
