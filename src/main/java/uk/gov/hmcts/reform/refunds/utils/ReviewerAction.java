package uk.gov.hmcts.reform.refunds.utils;

public enum ReviewerAction {
    APPROVE("approve"), REJECT("reject"), SENDBACK("sendback");

    private String reviewerAction;

    ReviewerAction(String reviewerAction){
        this.reviewerAction = reviewerAction;
    }

    public String getReviewerAction() {
        return this.reviewerAction;
    }

}
