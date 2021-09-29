package uk.gov.hmcts.reform.refunds.services;

import com.google.common.collect.Multimap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ContextStartListener implements ApplicationListener<ContextStartedEvent> {
    public static Map<String, List<UserIdentityDataDto>> userMap;

    @Autowired
    private IdamService idamService;

    @Override
    public void onApplicationEvent(ContextStartedEvent event) {
        System.out.println("Context Start Event received.");
        userMap = new ConcurrentHashMap<>();
        List<UserIdentityDataDto> userIdentityDataDtoList = idamService.getUsersForRoles(getAuthenticationHeaders(),
                                                                                         Arrays.asList("payments-refund","payments-refund-approver"));

        userMap.put("payments-refund", getUsersBasedOnRole(userIdentityDataDtoList,"payments-refund"));
        userMap.put("payments-refund-approver", getUsersBasedOnRole(userIdentityDataDtoList,"payments-refund-approver"));

    }


    private MultiValueMap<String, String>  getAuthenticationHeaders(){
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.add("Authorization", getAccessToken());
        return inputHeaders;
    }

    private String getAccessToken(){
        IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
        return idamTokenResponse.getAccessToken();
    }

    private List<UserIdentityDataDto> getUsersBasedOnRole(List<UserIdentityDataDto> userIdentityDataDtoList, String role){
        return userIdentityDataDtoList.stream().filter(userIdentityDataDto -> userIdentityDataDto.getRoles().contains(role)).collect(
            Collectors.toList());
    }
}
