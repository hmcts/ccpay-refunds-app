package uk.gov.hmcts.reform.refunds.smoke.idam.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder(builderMethodName = "userWith")
public class User {
    private final String email;
    private final String authorisationToken;
}
