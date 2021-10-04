package uk.gov.hmcts.reform.refunds;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class RefundApplication {

    public static void main(final String[] args) {
        SpringApplication.run(RefundApplication.class, args).start();
    }

}

