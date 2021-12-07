package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;


@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "remissionDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class RemissionResponse {

    private String remissionReference;

    private String beneficiaryName;

    private String ccdCaseNumber;

    private String caseReference;

    private String hwfReference;

    private BigDecimal hwfAmount;

    private String feeCode;

    private Integer feeId;

    private Date dateCreated;

}
