package uk.gov.hmcts.reform.refunds.state;

import java.util.Arrays;
    import java.util.List;
    import java.util.stream.Collectors;

public enum RefundEvent {
    SUBMIT,
    REJECT,
    SENDBACK,
    APPROVE,
    ACCEPT,
    CANCEL;

    public static List<String> getRefundStatus() {
        return Arrays.asList(RefundEvent.values()).stream().map(refundEvent -> refundEvent.name()).collect(Collectors.toList());
    }
}
