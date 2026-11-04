"""Check S2S policy structure; deployed APIM is required to execute the C# expressions."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


class S2sPolicyTest(unittest.TestCase):
    def test_lease_failure_is_handled_before_setting_service_authorization(self):
        template = Path(__file__).resolve().parents[1] / "template/cft-api-policy.xml"
        source = template.read_text()
        # Isolate the S2S section so these checks do not depend on certificate/Terraform configuration.
        section = source.split("<!-- generate totp using mgmt named values -->", 1)[1].split("</inbound>", 1)[0]
        inbound = list(ET.fromstring("<inbound>" + section + "</inbound>"))
        lease = next(i for i, node in enumerate(inbound) if node.tag == "send-request")
        failure_check = inbound[lease + 1].find("when")
        self.assertIn("StatusCode != 200", failure_check.get("condition"))
        self.assertEqual("502", failure_check.find("./return-response/set-status").get("code"))
        self.assertEqual("ServiceAuthorization", inbound[lease + 2].get("name"))
        self.assertEqual("override", inbound[lease + 2].get("exists-action"))


if __name__ == "__main__":
    unittest.main()
