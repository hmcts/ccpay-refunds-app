package uk.gov.hmcts.reform.refunds;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RefundApplicationTest {

    @Test
    void shouldHaveSpringBootApplication() {
        assertThat(RefundApplication.class).hasAnnotations(SpringBootApplication.class);
    }
}
