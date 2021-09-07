package uk.gov.hmcts.reform.refunds.state;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;


import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class RefundStateUtilTest extends StateUtil {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void NextStateForSubmitOrSentForApproval() throws Exception {

        RefundState refundState = RefundState.SENTFORAPPROVAL;
        assertEquals(RefundState.SENTTOMIDDLEOFFICE, refundState.nextState(RefundEvent.APPROVE));
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.REJECT));
        assertEquals(RefundState.NEEDMOREINFO, refundState.nextState(RefundEvent.SENDBACK));
    }

    @Test
    void NextStateForApprove() throws Exception {

        RefundState refundState = RefundState.SENTTOMIDDLEOFFICE;
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.REJECT));
        assertEquals(RefundState.ACCEPTED, refundState.nextState(RefundEvent.ACCEPT));
    }

    @Test
    void NextStateForNEEDMOREINFO() throws Exception {

        RefundState refundState = RefundState.NEEDMOREINFO;
        assertEquals(RefundState.SENTFORAPPROVAL, refundState.nextState(RefundEvent.SUBMIT));
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.CANCEL));
    }

    @Test
    void NextStateForAccept() throws Exception {

        RefundState refundState = RefundState.ACCEPTED;
        assertEquals(RefundState.ACCEPTED, refundState.nextState(RefundEvent.SUBMIT));
    }

    @Test
    void NextStateForReject() throws Exception {

        RefundState refundState = RefundState.REJECTED;
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.SUBMIT));
    }


    @Test
    void returnNullOnInvalidState() throws Exception {
        RefundState refundState = getRefundState("invalid state");
        assertEquals(null,refundState);
    }
}
