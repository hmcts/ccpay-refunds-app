package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "RefundRequestWith")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RefundStatusUpdateRequest {

    private String reason;

    @NotNull
    private RefundStatus status;

    @AssertFalse(message = "Refund status should be ACCEPTED or REJECTED/Refund rejection reason is missing")
    private boolean isReasonNotEmpty() {
        return status == REJECTED && StringUtils.isBlank(reason);
    }

    @Override
    public String toString() {
        return "RefundStatusUpdateRequest{" +
                "reason='" + reason + '\'' +
                ", status=" + status +
                '}';
    }
}
