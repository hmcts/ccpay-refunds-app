package uk.gov.hmcts.reform.refunds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.apache.commons.lang.StringUtils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "RefundRequestWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Getter
@Setter
public class RefundStatusUpdateRequest {

    private String reason;

    @NotNull
    private RefundStatus status;

    @AssertFalse(message = "Refund status should be ACCEPTED or REJECTED/Refund rejection reason is missing")
    private boolean isReasonNotEmpty() {
        return ((status == RefundStatus.REJECTED && !StringUtils.isNotBlank(reason)));
    }

}
