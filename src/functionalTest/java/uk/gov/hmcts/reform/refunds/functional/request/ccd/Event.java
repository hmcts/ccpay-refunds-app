package uk.gov.hmcts.reform.refunds.functional.request.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder()
@AllArgsConstructor
@NoArgsConstructor
@lombok.Data
public class Event {
    @Builder.Default
    public String id = "createDraft";
    @Builder.Default
    public String summary = "";
    @Builder.Default
    public String description = "";
}
