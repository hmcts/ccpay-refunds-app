package uk.gov.hmcts.reform.refunds.functional.response.idam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder()
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class IdamGetUserDetailsResponse {
    private String id;
    private String forename;
    private String surname;
    private String email;
}
