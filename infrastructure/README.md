# App infrastructure

Add any application specific infrastructure to the terraform files in this folder

This could be things like:
* a database
* redis
* vault
* application insights

If you don't need any application infrastructure here, you can delete the whole folder (it will speed up your Jenkins build)

## Client certificate authentication (CME-899)

The certificate compatibility changes follow
[payment-api-gateway PR #139](https://github.com/hmcts/ccpay-payment-api-gateway/pull/139/changes).
The trusted-source restriction addresses CME-899's spoofable-header finding. Its Terraform settings
and certificate policy must be deployed together: `variables.tf` declares the trusted gateway
subnets, `cft-api-mgmt.tf` renders the restriction into the existing API policy, and each environment's
`.tfvars` supplies its gateway subnet. These settings configure the existing API policy.

The Refund Status API accepts a whitelisted certificate presented directly to APIM, or the
`X-ARR-ClientCertThumbprint` header supplied by the CFT mTLS Application Gateway. The policy captures
the header before deleting it, prefers a directly presented certificate, and checks its UTC validity
window when available. A forwarded thumbprint cannot provide expiry or chain information: Application
Gateway must validate the original client certificate and overwrite the header from its TLS session.

Header-based authentication additionally restricts the connection source to
`trusted_client_certificate_gateway_cidrs` using APIM's `ip-filter` policy. An empty list rejects all
forwarded-certificate requests. Use the dedicated Application Gateway **backend source subnet** as
observed by APIM, not its public or private frontend IP, and do not use `X-Forwarded-For` as proof of
origin. The configured hub-appgw ranges were read from Azure on 4 November 2026:

| Environments | Virtual network | Source range |
| --- | --- | --- |
| aat, prod | hmcts-hub-prod-int | 10.11.8.192/27 |
| demo, ithc, perftest | hmcts-hub-nonprodi | 10.11.72.224/27 |

The subnet must remain dedicated to trusted gateways that enforce mTLS and overwrite the forwarded
header. Shared APIM and Application Gateway configuration is managed in
[azure-platform-terraform](https://github.com/hmcts/azure-platform-terraform/tree/master/components/apim-appgw).
This API policy does not change bulk-scanning OAuth2 policies.

After deploying to Demo, verify through the actual gateway before promoting:

1. A whitelisted, valid client certificate succeeds without forwarding the thumbprint header downstream.
2. Missing, expired, not-yet-valid and non-whitelisted certificates are rejected, including when the caller
   supplies a forged whitelisted header. Repeat with duplicate and differently cased header names.
3. A direct APIM request carrying only a forged whitelisted header is rejected, including from another
   reachable internal source. An actual valid direct client certificate must take precedence over headers.

Local checks:

```sh
python3 -B -m unittest discover -s infrastructure/tests -v
```

These checks use Terraform to render the real policy and verify its XML and source restrictions without
Azure credentials. They do not execute APIM's C# expressions or replace the deployed checks above.
