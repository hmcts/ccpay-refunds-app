package uk.gov.hmcts.reform.refunds.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotAllowedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class RefundServiceRoleUtil {

    private static final String SERVICE_NAME_REFUND_ROLE = "payments-refund";
    private static final String SERVICE_NAME_REFUND_APPROVAL_ROLE = "payments-refund-approver";

    private static final String AUTHORISED_REFUNDS_ROLE_REGEX = "^[payments]+-[refund]+-[a-zA-Z.-]+";
    private static final String AUTHORISED_REFUNDS_APPROVER_ROLE_REGEX = "^[payments]+-[refund]+-[approver]+-[a-zA-Z.-]+";

    private static final Logger LOG = LoggerFactory.getLogger(RefundServiceRoleUtil.class);

    public boolean validateRefundRoleWithServiceName(List<String> roles, String serviceName) {

        LOG.info("Validate Refund Role With Service Name ---> roles {}", roles.toString());
        LOG.info("Validate Refund Role With Service Name ---> serviceName {}", serviceName);
        String serviceNameRefundRole = SERVICE_NAME_REFUND_ROLE + "-" + serviceName;
        String serviceNameRefundApprovalRole = SERVICE_NAME_REFUND_APPROVAL_ROLE + "-" + serviceName;
        LOG.info("Validate Refund Role With Service Name ---> roles {}", roles.toString());
        LOG.info("Validate Refund Role With Service Name ---> serviceName {}", serviceName);
        List<String> refundServiceRoles = roles.stream().filter(role ->
                                               role.toLowerCase().contains(serviceNameRefundRole.toLowerCase())
                                               || role.toLowerCase().contains(serviceNameRefundApprovalRole.toLowerCase()))
                                               .collect(Collectors.toList());

        LOG.info("Validate Refund Role With Service Name ---> roles {}", roles.toString());
        LOG.info("Validate Refund Role With Service Name ---> serviceName {}", serviceName);
        LOG.info("Validate Refund Role With Service Name ---> refundServiceRoles {}", refundServiceRoles.toString());
        if (refundServiceRoles == null || refundServiceRoles.isEmpty()) {
            throw new ActionNotAllowedException("Action not allowed to user for given service name");
        }
        return true;
    }

    public List<String> getServiceNameFromUserRoles(List<String> roles) {

        HashSet<String> serviceNameSet = new HashSet<>();

        Pattern refundRolePattern = Pattern.compile(
            AUTHORISED_REFUNDS_ROLE_REGEX,
            Pattern.CASE_INSENSITIVE
        );

        Pattern refundApproverRolePattern = Pattern.compile(
            AUTHORISED_REFUNDS_APPROVER_ROLE_REGEX,
            Pattern.CASE_INSENSITIVE
        );

        for (String role : roles) {
            if (!role.equalsIgnoreCase(SERVICE_NAME_REFUND_ROLE) && !role.equalsIgnoreCase(SERVICE_NAME_REFUND_APPROVAL_ROLE)) {
                Matcher matcherApprover = refundApproverRolePattern.matcher(role);
                if (matcherApprover != null && matcherApprover.find()) {
                    serviceNameSet.add(role.toLowerCase().split("approver-")[1]);
                } else {
                    Matcher matcherRefundRole = refundRolePattern.matcher(role);
                    if (matcherRefundRole != null && matcherRefundRole.find()) {
                        serviceNameSet.add(role.toLowerCase().split("refund-")[1]);
                    }
                }
            }
        }
        List<String> returnList =  new ArrayList<>();
        returnList.addAll(serviceNameSet);
        return returnList;
    }
}
