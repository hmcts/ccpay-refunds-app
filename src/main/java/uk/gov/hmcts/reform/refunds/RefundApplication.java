package uk.gov.hmcts.reform.refunds;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class RefundApplication {

    public static void main(final String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(RefundApplication.class, args);
        applicationContext.start();
    }

}

