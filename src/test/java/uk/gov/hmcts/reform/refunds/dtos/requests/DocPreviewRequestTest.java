package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocPreviewRequestTest {

    private static Validator validator;
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void builderShouldCreateValidRequest() {
        Personalisation personalisation = Personalisation.personalisationRequestWith()
            .ccdCaseNumber("1111222233334444")
            .refundReference("RF-9999-8888-7777-6666")
            .customerReference("RC-1234-5678-9012-3456")
            .build();

        DocPreviewRequest request = DocPreviewRequest.docPreviewRequestWith()
            .paymentReference("RC-1234-5678-9012-3456")
            .paymentMethod("card")
            .paymentChannel("online")
            .serviceName("cmc")
            .recipientEmailAddress("citizen@example.com")
            .notificationType(NotificationType.EMAIL)
            .personalisation(personalisation)
            .templateId("template-abc")
            .build();

        Set<ConstraintViolation<DocPreviewRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no validation violations, but found: " + violations);
        assertEquals("card", request.getPaymentMethod());
        assertEquals("online", request.getPaymentChannel());
        assertEquals("cmc", request.getServiceName());
        assertEquals(NotificationType.EMAIL, request.getNotificationType());
        assertNotNull(request.getPersonalisation());
        assertEquals("template-abc", request.getTemplateId());
    }

    @Test
    void missingPaymentMethodShouldFailValidation() {
        DocPreviewRequest request = DocPreviewRequest.docPreviewRequestWith()
            .paymentReference("RC-1234-5678-9012-3456")
            .paymentChannel("online")
            .serviceName("cmc")
            .notificationType(NotificationType.EMAIL)
            .personalisation(Personalisation.personalisationRequestWith()
                .ccdCaseNumber("1111222233334444")
                .refundReference("RF-9999-8888-7777-6666")
                .customerReference("RC-1234-5678-9012-3456")
                .build())
            .build();

        Set<ConstraintViolation<DocPreviewRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("paymentMethod")));
    }

    @Test
    void jsonSnakeCaseShouldSerializeFieldsAsExpected() throws Exception {
        DocPreviewRequest request = DocPreviewRequest.docPreviewRequestWith()
            .paymentReference("RC-1234-5678-9012-3456")
            .paymentMethod("card")
            .paymentChannel("online")
            .serviceName("cmc")
            .recipientEmailAddress("citizen@example.com")
            .notificationType(NotificationType.EMAIL)
            .templateId("template-abc")
            .personalisation(Personalisation.personalisationRequestWith()
                .ccdCaseNumber("1111222233334444")
                .refundReference("RF-9999-8888-7777-6666")
                .customerReference("RC-1234-5678-9012-3456")
                .build())
            .build();

        String json = objectMapper.writeValueAsString(request);
        // Basic checks for snake_case keys
        assertTrue(json.contains("payment_reference"));
        assertTrue(json.contains("payment_method"));
        assertTrue(json.contains("payment_channel"));
        assertTrue(json.contains("service_name"));
        assertTrue(json.contains("template_id"));
        assertTrue(json.contains("recipient_email_address"));
        assertTrue(json.contains("notification_type"));
        assertTrue(json.contains("personalisation"));
    }
}

