package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "supplementaryInfoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SupplementaryInfo {

    @NotNull
    private String ccdCaseNumber;

    @NotNull
    private SupplementaryDetails supplementaryDetails;
}
