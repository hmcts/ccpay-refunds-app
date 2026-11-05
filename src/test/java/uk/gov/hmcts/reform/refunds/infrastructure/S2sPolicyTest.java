package uk.gov.hmcts.reform.refunds.infrastructure;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks policy structure; executing the C# expressions still requires APIM. */
class S2sPolicyTest {

    @Test
    void shouldRejectFailedLeaseBeforeSettingServiceAuthorization() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        String template = Files.readString(Path.of("infrastructure/template/cft-api-policy.xml"));
        Document policy = factory.newDocumentBuilder().parse(new InputSource(new StringReader(template)));

        Element lease = select(policy, "/policies/inbound/send-request[@response-variable-name='s2sBearerToken']");
        Element guard = select(lease, "following-sibling::*[1]");
        assertEquals("choose", guard.getTagName());
        Element failureCheck = select(guard, "when");
        assertTrue(failureCheck.getAttribute("condition").contains("context.Variables[\"s2sBearerToken\"]"));
        assertTrue(failureCheck.getAttribute("condition").contains("StatusCode != 200"));
        assertEquals("502", select(failureCheck, "return-response/set-status").getAttribute("code"));

        Element authorization = select(guard, "following-sibling::*[1]");
        assertEquals("set-header", authorization.getTagName());
        assertEquals("ServiceAuthorization", authorization.getAttribute("name"));
        assertEquals("override", authorization.getAttribute("exists-action"));
    }

    private static Element select(Node context, String expression) throws Exception {
        Node result = (Node) XPathFactory.newInstance().newXPath().evaluate(expression, context, XPathConstants.NODE);
        assertNotNull(result, "Missing policy element: " + expression);
        return (Element) result;
    }
}
