package uk.gov.hmcts.reform.refunds.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the committed policy structure and configuration without executing APIM expressions. */
class ApimPolicyTest {

    private static final Path INFRASTRUCTURE = Path.of("infrastructure");

    private Document policy;

    @BeforeEach
    void setUp() throws Exception {
        policy = parseXml(Files.readString(INFRASTRUCTURE.resolve("template/cft-api-policy.xml")));
    }

    @Test
    void shouldCaptureThumbprintBeforeDeletingItsHeader() throws Exception {
        Node capture = select(policy, "/policies/inbound/set-variable[@name='arr_cert_thumbprint']");
        Element deletion = (Element) select(policy,
            "/policies/inbound/set-header[@name='X-ARR-ClientCertThumbprint']");

        assertNotNull(capture);
        assertNotNull(deletion);
        assertTrue((capture.compareDocumentPosition(deletion) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertEquals("delete", deletion.getAttribute("exists-action"));
        assertEquals(1.0, XPathFactory.newInstance().newXPath().evaluate(
            "count(/policies/inbound/set-header[@name='X-ARR-ClientCertThumbprint'])",
            policy, XPathConstants.NUMBER));
    }

    @Test
    void shouldCheckForwardingSourceBeforeSelectingThumbprint() throws Exception {
        Node sourceCheck = select(policy,
            "/policies/inbound/choose/when[@condition='@(context.Request.Certificate == null)']");
        Node thumbprint = select(policy, "/policies/inbound/set-variable[@name='effective_client_thumbprint']");

        assertNotNull(sourceCheck);
        assertNotNull(thumbprint);
        assertEquals("${forwarded_certificate_source_policy}", sourceCheck.getTextContent().trim());
        assertTrue((sourceCheck.compareDocumentPosition(thumbprint) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
    }

    @Test
    void shouldRejectForwardingWhenTrustedSourcesAreUnconfigured() throws Exception {
        String configuration = Files.readString(INFRASTRUCTURE.resolve("cft-api-mgmt.tf"));
        Matcher fallback = Pattern.compile(
            "(?s)untrusted_certificate_source_policy\\s*=\\s*<<-XML\\s*(.*?)\\R\\s*XML"
        ).matcher(configuration);
        assertTrue(fallback.find(), "Missing fail-closed policy fragment");
        Document rejection = parseXml(fallback.group(1));
        Element status = (Element) select(rejection, "/return-response/set-status");
        assertNotNull(status);
        assertEquals("401", status.getAttribute("code"));

        assertTrue(Pattern.compile(
            "forwarded_certificate_source_policy\\s*=\\s*"
                + "length\\(var\\.trusted_client_certificate_gateway_cidrs\\)\\s*==\\s*0\\s*"
                + "\\?\\s*local\\.untrusted_certificate_source_policy\\s*:"
        ).matcher(configuration).find(), "Empty trusted sources must select the rejection policy");
        assertTrue(configuration.contains(
            "forwarded_certificate_source_policy = local.forwarded_certificate_source_policy"
        ), "The source policy must be passed into the API template");
    }

    @Test
    void shouldConfigurePrivateGatewaySourceRangesForEachEnvironment() throws Exception {
        Pattern assignment = Pattern.compile("trusted_client_certificate_gateway_cidrs\\s*=\\s*(\\[[^\\]]*\\])");
        ObjectMapper mapper = new ObjectMapper();
        for (String environment : List.of("aat", "demo", "ithc", "perftest", "prod")) {
            Matcher match = assignment.matcher(Files.readString(INFRASTRUCTURE.resolve(environment + ".tfvars")));
            assertTrue(match.find(), "Missing gateway source ranges for " + environment);
            String[] ranges = mapper.readValue(match.group(1), String[].class);
            assertTrue(ranges.length > 0, "Missing gateway source ranges for " + environment);
            for (String range : ranges) {
                String[] parts = range.split("/", -1);
                assertEquals(2, parts.length, "Expected CIDR notation: " + range);
                assertTrue(parts[0].matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"), range);
                InetAddress address = InetAddress.getByName(parts[0]);
                assertTrue(address instanceof Inet4Address && address.isSiteLocalAddress(), range);
                int prefix = Integer.parseInt(parts[1]);
                assertTrue(prefix > 0 && prefix <= 32, "Unrestricted or invalid source range: " + range);
            }
        }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Node select(Document document, String expression) throws Exception {
        return (Node) XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.NODE);
    }
}
