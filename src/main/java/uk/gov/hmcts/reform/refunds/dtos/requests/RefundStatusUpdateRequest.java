package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang.StringUtils;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "RefundRequestWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RefundStatusUpdateRequest {

    private String reason;

    @NotNull
    private RefundStatus status;

    @AssertFalse(message = "Refund status should be ACCEPTED or REJECTED/Refund rejection reason is missing")
    private boolean isReasonNotEmpty() {
        return ((status == RefundStatus.REJECTED && StringUtils.isBlank(reason)));
    }

}
