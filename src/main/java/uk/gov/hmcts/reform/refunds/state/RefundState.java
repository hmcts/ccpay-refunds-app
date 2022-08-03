package uk.gov.hmcts.reform.refunds.state;

import uk.gov.hmcts.reform.refunds.model.RefundStatus;

@SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
public enum RefundState {

    SENTFORAPPROVAL {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.APPROVE, RefundEvent.REJECT, RefundEvent.UPDATEREQUIRED, RefundEvent.CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent event) {
            switch (event) {
                case APPROVE:
                    return APPROVED;
                case REJECT:
                    return REJECTED;
                case UPDATEREQUIRED:
                    return NEEDMOREINFO;
                case CANCEL:
                    return CANCELLED;
                default:
                    return this;
            }
        }

        @Override
        public RefundStatus getRefundStatus() {
            return RefundStatus.SENTFORAPPROVAL;
        }
    },
    NEEDMOREINFO {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.SUBMIT, RefundEvent.CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case SUBMIT:
                    return SENTFORAPPROVAL;
                case CANCEL:
                    return CANCELLED;
                default:
                    return this;
            }
        }

        @Override
        public RefundStatus getRefundStatus() {
            return RefundStatus.UPDATEREQUIRED;
        }
    },
    APPROVED {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.ACCEPT, RefundEvent.REJECT};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case ACCEPT:
                    return ACCEPTED;
                case REJECT:
                    return REJECTED;
                case CANCEL:
                    return CANCELLED;
                default:
                    return this;

            }
        }

        @Override
        public RefundStatus getRefundStatus() {
            return RefundStatus.APPROVED;
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
        public RefundStatus getRefundStatus() {
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
        public RefundStatus getRefundStatus() {
            return RefundStatus.REJECTED;
        }
    },
    CANCELLED {
        @Override
        public RefundEvent[] nextValidEvents() {
            return RefundEvent.values();
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {
            return this;
        }

        @Override
        public RefundStatus getRefundStatus() {
            return RefundStatus.CANCELLED;
        }
    };

    public abstract RefundEvent[] nextValidEvents();

    public abstract RefundState nextState(RefundEvent refundEvent);

    public abstract RefundStatus getRefundStatus();
}