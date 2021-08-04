package uk.gov.hmcts.reform.refunds.state;


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
                    return APPROVED;
                case REJECT:
                    return REJECTED;
                case SENDBACK:
                    return NEEDMOREINFO;
                default:
                    return this;
            }
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
    },
    APPROVED {
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
    };

    public abstract RefundEvent[] nextValidEvents();

    public abstract RefundState nextState(RefundEvent refundEvent);
}

