package uk.gov.hmcts.reform.refunds.dtos;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SupplementaryDetailsResponseTest {

    @Test
    public void testSupplementaryDetailsResponse() {
        SupplementaryInfo supplementaryInfo1 = new SupplementaryInfo();
        SupplementaryInfo supplementaryInfo2 = new SupplementaryInfo();
        List<SupplementaryInfo> supplementaryInfoList = List.of(supplementaryInfo1, supplementaryInfo2);

        MissingSupplementaryInfo missingSupplementaryInfo = MissingSupplementaryInfo.missingSupplementaryInfoWith()
            .ccdCaseNumbers(List.of("1111-2222-3333-4444", "1111-2222-3333-5555"))
            .build();

        SupplementaryDetailsResponse response = SupplementaryDetailsResponse.supplementaryDetailsResponseWith()
            .supplementaryInfo(supplementaryInfoList)
            .missingSupplementaryInfo(missingSupplementaryInfo)
            .build();

        assertNotNull(response);
        assertEquals(2, response.getSupplementaryInfo().size());
        assertEquals(2, response.getMissingSupplementaryInfo().getCcdCaseNumbers().size());
        assertEquals("1111-2222-3333-4444", response.getMissingSupplementaryInfo().getCcdCaseNumbers().get(0));
        assertEquals("1111-2222-3333-5555", response.getMissingSupplementaryInfo().getCcdCaseNumbers().get(1));
    }
}
