package uk.gov.hmcts.reform.refunds.dtos.requests;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder(builderMethodName = "searchCriteriaWith")
public class RefundSearchCriteria {

    private Date startDate;

    private Date endDate;

    private String refundReference;

}

