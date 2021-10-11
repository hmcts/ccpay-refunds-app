package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.state.RefundEvent;

public enum ReviewerAction {
    APPROVE {
        @Override
        public RefundEvent getEvent() {
            return RefundEvent.APPROVE;
        }
    },

    REJECT {
        @Override
        public RefundEvent getEvent() {
            return RefundEvent.REJECT;
        }
    },

    SENDBACK {
        @Override
        public RefundEvent getEvent() {
            return RefundEvent.SENDBACK;
        }
    };

    public abstract RefundEvent getEvent();

}
