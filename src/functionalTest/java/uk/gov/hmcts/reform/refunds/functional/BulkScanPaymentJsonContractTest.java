package uk.gov.hmcts.reform.refunds.functional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import uk.gov.hmcts.reform.refunds.functional.request.BulkScanCcdPayment;
import uk.gov.hmcts.reform.refunds.functional.request.BulkScanDcnPayment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Pins the JSON of the Bulk Scan payment requests that the functional tests send to set up
 * their test data: field names, explicit @JsonProperty overrides (e.g. site_id), booleans,
 * decimal amounts and dates, and that the @JsonIgnore validation helpers stay out of the payload.
 * Protects against a payload change surfacing only as a confusing Bulk Scan 400 in a deployed
 * functional run. Plain serialization test with no network; it runs in the functional task only
 * because the DTOs live in that source set.
 */
public class BulkScanPaymentJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void preservesCcdPaymentFieldNamesAndExplicitOverrides() throws Exception {
        String json = "{\"site_id\":\"AA08\",\"ccd_case_number\":\"1111222233334444\",\"is_exception_record\":false,"
            + "\"document_control_numbers\":[\"123456789012345678901\"]}";
        BulkScanCcdPayment request = BulkScanCcdPayment.createBSPaymentRequestWith()
            .responsibleServiceId("AA08").ccdCaseNumber("1111222233334444").isExceptionRecord(false)
            .documentControlNumbers(new String[]{"123456789012345678901"}).build();

        assertThat(mapper.readTree(mapper.writeValueAsString(request))).isEqualTo(mapper.readTree(json));
        assertThat(mapper.readValue(json, BulkScanCcdPayment.class)).isEqualTo(request);
    }

    @Test
    public void preservesDcnPaymentFieldNamesAndDecimalAmount() throws Exception {
        String json = "{\"document_control_number\":\"123456789012345678901\",\"amount\":123.45,\"currency\":\"GBP\","
            + "\"method\":\"Cheque\",\"bank_giro_credit_slip_number\":123456,\"banked_date\":\"2026-01-15\"}";
        BulkScanDcnPayment request = BulkScanDcnPayment.createPaymentRequestWith()
            .dcnReference("123456789012345678901").amount(new BigDecimal("123.45")).currency("GBP")
            .method("Cheque").bankGiroCreditSlipNumber(123456).bankedDate("2026-01-15").build();

        assertThat(mapper.readTree(mapper.writeValueAsString(request))).isEqualTo(mapper.readTree(json));
        assertThat(mapper.readValue(json, BulkScanDcnPayment.class)).isEqualTo(request);
    }
}
