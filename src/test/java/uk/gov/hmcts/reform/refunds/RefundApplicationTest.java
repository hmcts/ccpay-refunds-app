package uk.gov.hmcts.reform.refunds;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RefundApplication.class)
public class RefundApplicationTest {

    @Test
    public void test()
    {
        RefundApplication.main(new String[]{
            "--spring.main.web-environment=false",
            "--spring.autoconfigure.exclude=blahblahblah",
            // Override any other environment properties according to your needs
        });
    }
}
