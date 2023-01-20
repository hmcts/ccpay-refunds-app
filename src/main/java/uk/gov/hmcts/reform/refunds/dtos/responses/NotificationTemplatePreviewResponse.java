package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Builder(builderMethodName = "buildNotificationTemplatePreviewWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NotificationTemplatePreviewResponse {

    private String templateId;
    private String templateType;
    private FromTemplateContact from;
    private String subject;
    private String html;
    private RecipientContact recipientContact;
    private String body;
}
