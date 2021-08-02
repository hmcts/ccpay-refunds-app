package uk.gov.hmcts.reform.refunds.state;


public enum RefundState {
    submitted {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.APPROVE, RefundEvent.REJECT, RefundEvent.SENDBACK};
        }

        @Override
        public RefundState nextState(RefundEvent event) {
            switch (event) {
                case APPROVE:
                    return approved;
                case REJECT:
                    return rejected;
                case SENDBACK:
                    return needmoreinfo;
            }
            return this;
        }
    },
    needmoreinfo {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.SUBMIT, RefundEvent.CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case SUBMIT:
                    return submitted;
                case CANCEL:
                    return rejected;
            }
            return this;
        }
    },
    approved {
        @Override
        public RefundEvent[] nextValidEvents() {
            return new RefundEvent[]{RefundEvent.ACCEPT,RefundEvent.CANCEL};
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {

            switch (refundEvent) {
                case ACCEPT:
                    return accepted;
                case CANCEL:
                    return rejected;
            }
            return this;
        }
    },
    accepted {
        @Override
        public RefundEvent[] nextValidEvents() {
            return RefundEvent.values();
        }

        @Override
        public RefundState nextState(RefundEvent refundEvent) {
            return this;
        }
    },
    rejected {
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

