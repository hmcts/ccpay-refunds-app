package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder(builderMethodName = "userIdentityDataWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UserIdentityDataDto {
    private String fullName;
    private String emailId;
}
