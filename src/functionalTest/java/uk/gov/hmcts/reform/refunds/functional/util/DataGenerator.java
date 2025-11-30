package uk.gov.hmcts.reform.refunds.functional.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

@Component
public class DataGenerator {

    public String generateEmail(int length) {
        String allowedChars = "abcdefghijklmnopqrstuvwxyz"
            + "1234567890";//numbers

        String random = RandomStringUtils.random(length, allowedChars);
        String email = random + "@mailtest.gov.uk";
        return email;
    }
}
