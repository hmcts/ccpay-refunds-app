package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Builder(builderMethodName = "buildStatusHistoryListWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(NON_NULL)
public class StatusHistoryListDtoResponse {

    private List<StatusHistoryDto> statusHistoryDtoList;

}
