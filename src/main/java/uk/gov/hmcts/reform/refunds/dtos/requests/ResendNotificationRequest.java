package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "resendNotificationRequest")
public class ResendNotificationRequest {

    private String templateId;

    private String recipientEmailAddress;

    private String reference;

    private RecipientPostalAddress recipientPostalAddress;

    private NotificationType notificationType;

}
