package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.RefundFees;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.math.BigDecimal;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Builder(builderMethodName = "buildRefundListDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
public class RefundDto {

    private String ccdCaseNumber;

    private BigDecimal amount;

    private String reason;

    private RefundStatus refundStatus;

    private String refundReference;

    private String paymentReference;

    private String userFullName;

    private String emailId;

    private String dateCreated;

    private String dateUpdated;

    private String serviceType;

    private ContactDetails contactDetails;

    private String feeIds;

    private List<RefundFees> refundFees;


}
