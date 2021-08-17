package uk.gov.hmcts.reform.refunds.state;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
public class RefundStateTest {


    @Test
    public void NextStateForSubmitOrSentForApproval() throws Exception {

        RefundState refundState = RefundState.SENTFORAPPROVAL;
        assertEquals(refundState.nextState(RefundEvent.APPROVE), RefundState.SENTTOMIDDLEOFFICE);
        assertEquals(refundState.nextState(RefundEvent.REJECT), RefundState.REJECTED);
        assertEquals(refundState.nextState(RefundEvent.SENDBACK), RefundState.NEEDMOREINFO);
    }

    @Test
    public void NextStateForApprove() throws Exception {

        RefundState refundState = RefundState.SENTTOMIDDLEOFFICE;
        assertEquals(refundState.nextState(RefundEvent.CANCEL), RefundState.REJECTED);
        assertEquals(refundState.nextState(RefundEvent.ACCEPT), RefundState.ACCEPTED);
    }

    @Test
    public void NextStateForNEEDMOREINFO() throws Exception {

        RefundState refundState = RefundState.NEEDMOREINFO;
        assertEquals(refundState.nextState(RefundEvent.SUBMIT), RefundState.SENTFORAPPROVAL);
        assertEquals(refundState.nextState(RefundEvent.CANCEL), RefundState.REJECTED);
    }

    @Test
    public void NextStateForAccept() throws Exception {

        RefundState refundState = RefundState.ACCEPTED;
        assertEquals(refundState.nextState(RefundEvent.SUBMIT), RefundState.ACCEPTED);
    }

    @Test
    public void NextStateForReject() throws Exception {

        RefundState refundState = RefundState.REJECTED;
        assertEquals(refundState.nextState(RefundEvent.SUBMIT), RefundState.REJECTED);
    }
}
