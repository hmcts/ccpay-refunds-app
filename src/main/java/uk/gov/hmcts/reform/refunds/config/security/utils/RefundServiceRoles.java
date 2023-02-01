package uk.gov.hmcts.reform.refunds.config.security.utils;

public enum RefundServiceRoles {

    DIVORCE("Divorce", "payments-refund-divorce", "payments-refund-approverr-divorce"),
    CIVIL("Divorce", "payments-refund-civil", "payments-refund-approver-civil"),
    FAMILY_PUBLIC_LAW("Family Public Law","payments-refund-family-public-law",
                      "payments-refund-approver-family-public-law"),
    SPECIFIED_MONEY_CLAIMS("Specified Money Claims","payments-refund-specified-money-claims",
                           "payments-refund-approver-specified-money-claims"),
    ADOPTION("Adoption","payments-refund-adoption",
             "payments-refund-approver-adoption"),
    IMMIGRATION_AND_ASYLUM_APPEALS("Immigration and Asylum Appeals",
                                   "payments-refund-immigration-and-asylum-appeals",
                                   "payments-refund-approver-immigration-and-asylum-appeals"),
    CIVIL_MONEY_CLAIMS("Civil Money Claims","payments-refund-civil-money-claims",
                       "payments-refund-approver-civil-money-claims"),
    FINREM("finrem", "payments-refund-finrem", "payments-refund-approver-finrem"),
    FINANCIAL_REMEDY("Financial Remedy", "payments-refund-financial-remedy",
                     "payments-refund-approver-financial-remedy"),
    FAMILY_PRIVATE_LAW("Family Private Law", "payments-refund-family-private-law",
                       "payments-refund-approver-family-private-law"),
    PROBATE("Probate", "payments-refund-probate", "payments-refund-approver-probate");

    private String serviceName;

    private String paymentRefundRole;

    private String paymentRefundApprovalRole;


    RefundServiceRoles(String serviceName, String paymentRefundRole, String paymentRefundApprovalRole) {
        this.serviceName = serviceName;
        this.paymentRefundRole = paymentRefundRole;
        this.paymentRefundApprovalRole = paymentRefundApprovalRole;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getPaymentRefundRole() {
        return paymentRefundRole;
    }

    public String getPaymentRefundApprovalRole() {
        return paymentRefundApprovalRole;
    }

}
