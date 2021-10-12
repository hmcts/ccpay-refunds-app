package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Builder(builderMethodName = "userIdentityDataWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserIdentityDataDto {
    private String fullName;
    private String emailId;
    private String id;
    private List<String> roles;
}
