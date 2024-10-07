package uk.gov.hmcts.reform.refunds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
@EnableFeignClients(basePackages = {"uk.gov.hmcts.reform.idam"})
public class RefundApplication {

    public static void main(final String[] args) {
        SpringApplication.run(RefundApplication.class, args).start();
    }

}

