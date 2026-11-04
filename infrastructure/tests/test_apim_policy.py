"""Render the real Terraform policy without Azure credentials or provider downloads.

Run: python3 -m unittest discover -s infrastructure/tests -v
These checks validate rendering and policy structure; APIM expression execution needs a deployed gateway.
"""

import ipaddress
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


INFRASTRUCTURE = Path(__file__).resolve().parents[1]


class ApimPolicyRenderingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix="ccpay-policy-test-")
        policy_config = (INFRASTRUCTURE / "cft-api-mgmt.tf").read_text()
        variables = (INFRASTRUCTURE / "variables.tf").read_text()
        # Use the production expressions and input validation, isolated from remote modules/providers.
        locals_block = re.search(r"locals \{.*?\n\}", policy_config, re.DOTALL).group()
        trust_variable = re.search(
            r'variable "trusted_client_certificate_gateway_cidrs" \{.*?\n\}', variables, re.DOTALL
        ).group()
        (Path(cls.directory.name) / "main.tf").write_text(
            'variable "env" { default = "demo" }\n'
            'variable "apim_suffix" { default = "" }\n'
            + trust_variable + "\n" + locals_block
        )

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def render(self, cidrs, thumbprints=None):
        allowed = ",".join("&quot;" + value + "&quot;" for value in (thumbprints or []))
        expression = "templatefile(" + json.dumps(str(INFRASTRUCTURE / "template/cft-api-policy.xml")) + ", {" + (
            "allowed_certificate_thumbprints = " + json.dumps(allowed) + ", "
            's2s_base_url = "https://s2s.example.test", '
            "forwarded_certificate_source_policy = local.forwarded_certificate_source_policy })"
        )
        result = subprocess.run(
            ["terraform", "console", "-no-color", "-var=trusted_client_certificate_gateway_cidrs=" + json.dumps(cidrs)],
            input="jsonencode(" + expression + ")\n", capture_output=True, text=True,
            cwd=self.directory.name, check=True, timeout=30,
        )
        return ET.fromstring(json.loads(json.loads(result.stdout.strip())))

    def test_unconfigured_forwarding_is_rejected(self):
        policy = self.render([])
        forwarding = policy.find("./inbound/choose/when")
        self.assertEqual("@(context.Request.Certificate == null)", forwarding.get("condition"))
        self.assertEqual("401", forwarding.find("./return-response/set-status").get("code"))
        self.assertIsNone(forwarding.find("ip-filter"))

    def test_environment_source_filters_use_the_configured_subnet_boundaries(self):
        for environment in ("aat", "demo", "ithc", "perftest", "prod"):
            with self.subTest(environment=environment):
                tfvars = (INFRASTRUCTURE / (environment + ".tfvars")).read_text()
                cidrs = json.loads(re.search(
                    r"trusted_client_certificate_gateway_cidrs\s*=\s*(\[[^\]]*\])", tfvars
                ).group(1))
                policy = self.render(cidrs, ["AbCdEf", "123456"])
                ip_filter = policy.find("./inbound/choose/when/ip-filter")
                self.assertEqual("allow", ip_filter.get("action"))
                self.assertEqual(len(cidrs), len(ip_filter.findall("address-range")))
                for cidr, address_range in zip(cidrs, ip_filter.findall("address-range")):
                    network = ipaddress.ip_network(cidr)
                    self.assertEqual(str(network.network_address), address_range.get("from"))
                    self.assertEqual(str(network.broadcast_address), address_range.get("to"))

    def test_thumbprint_is_captured_before_deletion_and_not_forwarded(self):
        inbound = list(self.render(["10.11.8.192/27"]).find("inbound"))
        capture = next(i for i, node in enumerate(inbound) if node.get("name") == "arr_cert_thumbprint")
        deletion = next(i for i, node in enumerate(inbound) if node.get("name") == "X-ARR-ClientCertThumbprint")
        self.assertLess(capture, deletion)
        self.assertEqual("delete", inbound[deletion].get("exists-action"))
        self.assertEqual(1, sum(node.get("name") == "X-ARR-ClientCertThumbprint" for node in inbound))

    def test_invalid_or_unrestricted_forwarding_ranges_are_rejected(self):
        for cidr in ("0.0.0.0/0", "invalid", "::/0"):
            with self.subTest(cidr=cidr):
                with self.assertRaises(subprocess.CalledProcessError):
                    self.render([cidr])


if __name__ == "__main__":
    unittest.main()
