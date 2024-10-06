package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "supplementaryDetailsResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SupplementaryDetailsResponse {

    @NotNull
    private List<SupplementaryInfo> supplementaryInfo;

    @NotNull
    private MissingSupplementaryInfo missingSupplementaryInfo;
}
