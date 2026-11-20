package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.refunds.model.IacSupplementaryRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Pins the snake_case JSON contract for the supplementary case information exchanged with IAC:
 * serialization, deserialization and null omission against fixed payloads, as UTF-8 bytes so the
 * non-ASCII surname is really encoded.
 * Protects against renamed or re-cased fields, nulls leaking into requests, and lost case details
 * on read. Service tests build these DTOs directly and mock RestTemplate, so they would miss this.
 */
class SupplementaryJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}: serializes the published JSON field names")
    @MethodSource("contracts")
    void serializesSnakeCaseContract(String name, Object value, String json) throws Exception {
        JsonNode actual = mapper.readTree(mapper.writeValueAsBytes(value));
        assertThat(actual).isEqualTo(mapper.readTree(json));
    }

    @ParameterizedTest(name = "{0}: reads the published JSON field names")
    @MethodSource("contracts")
    void deserializesSnakeCaseContract(String name, Object value, String json) throws Exception {
        Object decoded = mapper.readValue(json.getBytes(StandardCharsets.UTF_8), value.getClass());
        assertThat(decoded).usingRecursiveComparison().isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("emptyValues")
    void omitsNullProperties(Object value) {
        JsonNode actual = mapper.valueToTree(value);
        assertThat(actual).isEqualTo(mapper.createObjectNode());
    }

    private static Stream<Arguments> contracts() {
        SupplementaryDetails details = new SupplementaryDetails("O'Neill–Jones", "IAC/2026/1234");
        SupplementaryInfo info = new SupplementaryInfo("1111222233334444", details);
        MissingSupplementaryInfo missing = new MissingSupplementaryInfo(List.of("5555666677778888"));
        String detailsJson = "{\"surname\":\"O'Neill–Jones\",\"case_reference_number\":\"IAC/2026/1234\"}";
        String infoJson = "{\"ccd_case_number\":\"1111222233334444\",\"supplementary_details\":" + detailsJson + "}";
        String missingJson = "{\"ccd_case_numbers\":[\"5555666677778888\"]}";
        return Stream.of(
            Arguments.of("supplementary details", details, detailsJson),
            Arguments.of("supplementary info", info, infoJson),
            Arguments.of("missing cases", missing, missingJson),
            Arguments.of("supplementary response", new SupplementaryDetailsResponse(List.of(info), missing),
                "{\"supplementary_info\":[" + infoJson + "],\"missing_supplementary_info\":" + missingJson + "}"),
            Arguments.of("IAC request", new IacSupplementaryRequest(List.of("1111222233334444")),
                "{\"ccd_case_numbers\":[\"1111222233334444\"]}")
        );
    }

    private static Stream<Object> emptyValues() {
        return Stream.of(new SupplementaryDetails(), new SupplementaryInfo(), new MissingSupplementaryInfo(),
            new SupplementaryDetailsResponse(), new IacSupplementaryRequest());
    }
}
