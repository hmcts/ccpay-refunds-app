package uk.gov.hmcts.reform.refunds.config.security.utils;

public enum RefundServiceRoles {

    DIVORCE("Divorce", "payments-refund-divorce", "payments-refund-approve-divorce"),
    CIVIL("Divorce", "payments-refund-civil", "payments-refund-approve-civil"),
    FAMILY_PUBLIC_LAW("Family Public Law","payments-refund-family-public-law",
                      "payments-refund-approve-family-public-law"),
    SPECIFIED_MONEY_CLAIMS("Specified Money Claims","payments-refund-specified-money-claims",
                           "payments-refund-approve-specified-money-claims"),
    ADOPTION("Adoption","payments-refund-adoption",
             "payments-refund-approve-adoption"),
    IMMIGRATION_AND_ASYLUM_APPEALS("Immigration and Asylum Appeals",
                                   "payments-refund-immigration-and-asylum-appeals",
                                   "payments-refund-approve-immigration-and-asylum-appeals"),
    CIVIL_MONEY_CLAIMS("Civil Money Claims","payments-refund-civil-money-claims",
                       "payments-refund-approve-civil-money-claims"),
    FINREM("finrem", "payments-refund-finrem", "payments-refund-approve-finrem"),
    FINANCIAL_REMEDY("Financial Remedy", "payments-refund-financial-remedy",
                     "payments-refund-approve-financial-remedy"),
    FAMILY_PRIVATE_LAW("Family Private Law", "payments-refund-family-private-law",
                       "payments-refund-approve-family-private-law"),
    PROBATE("Probate", "payments-refund-probate", "payments-refund-approve-probate");

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
