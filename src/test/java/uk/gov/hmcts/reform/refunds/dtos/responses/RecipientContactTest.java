package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.refunds.dtos.requests.MailAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientContactTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void builderShouldCreateEmailRecipientContact() {
        RecipientContact contact = RecipientContact.buildRecipientContactWith()
            .recipientEmailAddress("citizen@example.com")
            .build();

        assertEquals("citizen@example.com", contact.getRecipientEmailAddress());
        assertNull(contact.getRecipientMailAddress());
    }

    @Test
    void builderShouldCreatePostalRecipientContact() {
        MailAddress address = MailAddress.buildRecipientMailAddressWith()
            .addressLine("10 Downing Street")
            .city("London")
            .county("Greater London")
            .country("UK")
            .postalCode("SW1A 2AA")
            .build();

        RecipientContact contact = RecipientContact.buildRecipientContactWith()
            .recipientMailAddress(address)
            .build();

        assertNotNull(contact.getRecipientMailAddress());
        assertEquals("10 Downing Street", contact.getRecipientMailAddress().getAddressLine());
        assertEquals("London", contact.getRecipientMailAddress().getCity());
        assertEquals("Greater London", contact.getRecipientMailAddress().getCounty());
        assertEquals("UK", contact.getRecipientMailAddress().getCountry());
        assertEquals("SW1A 2AA", contact.getRecipientMailAddress().getPostalCode());
    }

    @Test
    void jsonSnakeCaseShouldSerializeFieldsAsExpected() throws Exception {
        RecipientContact contact = RecipientContact.buildRecipientContactWith()
            .recipientEmailAddress("citizen@example.com")
            .build();

        String json = mapper.writeValueAsString(contact);
        assertTrue(json.contains("recipient_email_address"));
        assertTrue(json.contains("citizen@example.com"));
    }
}
