package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder(builderMethodName = "refundNotificationEmailRequestWith")
public class RefundNotificationEmailRequest {

    private String templateId;

    private String recipientEmailAddress;

    private String reference;

    private String emailReplyToId;

    private NotificationType notificationType;

    private Personalisation personalisation;

    private String serviceName;

    private TemplatePreview templatePreview;

}
