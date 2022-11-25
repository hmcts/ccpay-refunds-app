package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "templatePreviewDtoWith")
@JsonInclude(NON_NULL)
@Setter
@Getter
public class TemplatePreview {

    private UUID id;

    private String templateType;

    private int version;

    private String body;

    private String subject;

    private String html;
}
