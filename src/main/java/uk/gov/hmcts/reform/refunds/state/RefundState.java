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

    SENTFORAPPROVAL {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{APPROVE, REJECT, SENDBACK};
        }

        @Override
        public RefundState nextState(RefundEvent event) {
            switch (event) {
                case APPROVE:
                    return SENTTOMIDDLEOFFICE;
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
            return RefundStatus.SENTFORAPPROVAL;
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
                    return SENTFORAPPROVAL;
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
    SENTTOMIDDLEOFFICE{
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
            return RefundStatus.SENTTOMIDDLEOFFICE;
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
