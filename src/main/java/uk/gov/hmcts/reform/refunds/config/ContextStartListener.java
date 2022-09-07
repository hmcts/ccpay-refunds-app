package uk.gov.hmcts.reform.refunds.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.services.IdamService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ContextStartListener implements ApplicationListener<ContextRefreshedEvent> {
    private static Map<String, List<UserIdentityDataDto>> userMap;
    private static final Logger LOG = LoggerFactory.getLogger(ContextStartListener.class);


    @Autowired
    private IdamService idamService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        LOG.info("Context Start Event received.");
        userMap = new ConcurrentHashMap<>();
        List<UserIdentityDataDto> userIdentityDataDtoList = idamService.getUsersForRoles(getAuthenticationHeaders(),
                                                                                         Arrays.asList("payments-refund","payments-refund-approver"));
        userMap.put("payments-refund",userIdentityDataDtoList);

    }

    public Map<String, List<UserIdentityDataDto>> getUserMap() {
        return userMap;
    }

    public void addUserToMap(String userGroup, UserIdentityDataDto userIdentityDataDto) {
        List<UserIdentityDataDto> userIdentityDataDtoList = userMap.get(userGroup);
        userIdentityDataDtoList.add(userIdentityDataDto);
        userMap.put("payments-refund",userIdentityDataDtoList);
    }

    private MultiValueMap<String, String>  getAuthenticationHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.add("Authorization", getAccessToken());
        return inputHeaders;
    }

    private String getAccessToken() {
        IdamTokenResponse idamTokenResponse = idamService.getSecurityTokens();
        return idamTokenResponse.getAccessToken();
    }

}
