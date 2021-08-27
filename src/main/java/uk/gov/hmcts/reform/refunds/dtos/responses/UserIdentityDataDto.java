package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.*;

@Builder(builderMethodName = "userIdentityDataWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserIdentityDataDto {
    private String fullName;
    private String emailId;
}
