#!/usr/bin/env bash
#
# Run the Refunds API functional tests against a deployed (AAT/Demo) environment.
#
# Environment variables and secrets are sourced exclusively from Jenkinsfile_CNP.
# Secret VALUES are fetched from Azure Key Vault at runtime and never persisted or
# committed - only the vault/secret names and target env var names live in this file.
#
# Usage:
#   ./scripts/run-functional-tests.sh                # run all functional tests
#   ./scripts/run-functional-tests.sh FooTest        # run a single test class
#   ./scripts/run-functional-tests.sh FooTest demo   # run a single test class against Demo
#
# Prerequisites:
#   - Connected to the HMRC VPN (so the internal .internal URLs resolve)
#   - Logged in to the Azure CLI:  `az login`

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration (mirrors Jenkinsfile_CNP)
# ---------------------------------------------------------------------------
ENVIRONMENT="${2:-aat}"
VAULT="ccpay-${ENVIRONMENT}"                       # = ccpay-aat / ccpay-demo

# test.url.refunds points at the deployed refunds-api for the environment, e.g.
#   http://ccpay-refunds-api-aat.service.core-compute-aat.internal
TEST_URL="http://ccpay-refunds-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
# From Jenkinsfile_CNP `before('functionalTest:aat')`
TEST_URL_PAYMENT="http://payment-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
BULKSCAN_API_URL="http://ccpay-bulkscanning-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"

export TEST_URL
export TEST_URL_PAYMENT
export BULKSCAN_API_URL

# Jenkins agents run in UTC; enforce the same on the local JVM.
export TZ=UTC
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Duser.timezone=UTC"
export JAVA_TOOL_OPTIONS

usage() {
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
}

SINGLE_TEST="${1:-}"

if [[ "${SINGLE_TEST}" == "-h" || "${SINGLE_TEST}" == "--help" ]]; then
    usage
    exit 0
fi

# ---------------------------------------------------------------------------
# Azure check - verify the session is logged in AND its token is still valid.
# A stale/expired token passes `az account show` but fails `az keyvault`, so
# probe the vault explicitly for a clear single error rather than one per secret.
# ---------------------------------------------------------------------------
az account show >/dev/null 2>&1 || {
    echo "ERROR: Not logged into Azure. Run 'az login' (and connect to the VPN) first." >&2
    exit 1
}

if ! az keyvault secret show --vault-name "$VAULT" --name paybubble-s2s-secret \
        --query value --output tsv >/dev/null 2>&1; then
    echo "ERROR: Cannot read secrets from vault '$VAULT' (${VAULT})." >&2
    echo "       Your Azure token may have expired. Re-authenticate with:  az login" >&2
    echo "       (Also ensure you are on the HMRC VPN and have access to '${VAULT}')." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Secret helpers - pull a secret from the vault, never persist it to a file.
# Azure failures are surfaced (not silently swallowed) but do not abort the
# script - the sanity check below reports what is missing.
# ---------------------------------------------------------------------------
require() {
    # require <envVarName> <secretName>
    local env_name="$1" value rc
    set +e
    value="$(az keyvault secret show --vault-name "$VAULT" --name "$2" \
            --query value --output tsv 2>&1)"
    rc=$?
    set -e
    if [ "$rc" -ne 0 ]; then
        echo "WARNING: could not fetch '$2' from vault '$VAULT' for env '$1':" >&2
        echo "         $value" >&2
        return 0
    fi
    export "$env_name=$value"
}

# ---------------------------------------------------------------------------
# Load secrets from Key Vault (from the `secrets` block in Jenkinsfile_CNP)
# ---------------------------------------------------------------------------
require TEST_USER_PASSWORD                       freg-idam-test-user-password
require GENERATED_USER_EMAIL_PATTERN             freg-idam-generated-user-email-pattern
require OIDC_CLIENT_SECRET                       paybubble-idam-client-secret
require IDAM_PAYBUBBLE_CLIENT_SECRET             paybubble-idam-client-secret
require OAUTH2_CLIENT_SECRET                     citizen-oauth-client-secret
require S2S_SERVICE_SECRET_PAYMENT_APP           payment-s2s-secret
require S2S_SERVICE_SECRET_CMC                   cmc-service-secret
require S2S_SERVICE_SECRET_PAYBUBBLE             paybubble-s2s-secret
require NOTIFY_API_KEY                           notifications-email-apikey
require PROBATE_CASE_WORKER_USER_NAME            probate-caseworker-username
require PROBATE_CASE_WORKER_PASSWORD             probate-caseworker-password

# Sanity check the secrets the tests need most
for env_var in TEST_USER_PASSWORD OIDC_CLIENT_SECRET S2S_SERVICE_SECRET_PAYMENT_APP; do
    if [ -z "${!env_var:-}" ]; then
        echo "ERROR: required secret '$env_var' is empty - cannot run functional tests." >&2
        exit 1
    fi
done

# ---------------------------------------------------------------------------
# Run the tests
# ---------------------------------------------------------------------------
if [ -n "$SINGLE_TEST" ]; then
    echo "Running single functional test class: $SINGLE_TEST (env: $ENVIRONMENT)"
    ./gradlew functional \
        --tests "uk.gov.hmcts.reform.refunds.functional.${SINGLE_TEST}" \
        --rerun-tasks
else
    echo "Running all functional tests (env: $ENVIRONMENT)"
    ./gradlew --console plain functional
fi
