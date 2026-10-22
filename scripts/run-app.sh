#!/usr/bin/env bash
#
# Run the Refunds API application locally (gradlew bootRun) against a remote
# (AAT/Demo) environment.
#
# URLs are derived from charts/ccpay-refunds-api/values.yaml. Secret VALUES are
# fetched from Azure Key Vault at runtime and never persisted or committed - only
# the vault/secret names and target env var names live in this file.
#
# Usage:
#   ./scripts/run-app.sh                 # run against AAT
#   ./scripts/run-app.sh demo            # run against Demo
#
# Prerequisites:
#   - Connected to the HMRC VPN (so the internal .internal URLs resolve)
#   - Logged in to the Azure CLI:  `az login`

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration (mirrors charts/ccpay-refunds-api/values.yaml)
# ---------------------------------------------------------------------------
ENVIRONMENT="${1:-aat}"
VAULT="ccpay-${ENVIRONMENT}"                       # = ccpay-aat / ccpay-demo

# Internal service URLs (from values.yaml environment block, ${ENV} interpolated)
AUTH_PROVIDER_SERVICE_CLIENT_BASEURL="http://rpe-service-auth-provider-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
AUTH_IDAM_CLIENT_BASEURL="https://idam-api.${ENVIRONMENT}.platform.hmcts.net"
ISSUER_URI="https://idam-web-public.${ENVIRONMENT}.platform.hmcts.net/o"
IDAM_API_URL="https://idam-api.${ENVIRONMENT}.platform.hmcts.net"
OIDC_ISSUER="https://forgerock-am.service.core-compute-idam-${ENVIRONMENT}.internal:8443/openam/oauth2/hmcts"
PAYMENT_API_URL="http://payment-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
NOTIFICATION_API_URL="http://ccpay-notifications-service-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
IAC_SERVICE_API_URL="http://ia-case-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
BULKSCAN_API_URL="http://ccpay-bulkscanning-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal"
REFUND_SERVICE_ACCOUNT_REDIRECT_URI="http://ccpay-refunds-api-${ENVIRONMENT}.service.core-compute-${ENVIRONMENT}.internal/oauth2/callback"

export AUTH_PROVIDER_SERVICE_CLIENT_BASEURL
export AUTH_IDAM_CLIENT_BASEURL
export ISSUER_URI
export IDAM_API_URL
export OIDC_ISSUER
export PAYMENT_API_URL
export NOTIFICATION_API_URL
export IAC_SERVICE_API_URL
export BULKSCAN_API_URL
export REFUND_SERVICE_ACCOUNT_REDIRECT_URI

# Non-secret application settings (from values.yaml environment block)
export OIDC_CLIENT_ID="paybubble"
export OIDC_S2S_MICROSERVICE_NAME="refunds_api"
export OIDC_AUDIENCE_LIST="paybubble,cmc_citizen,ccd_gateway,xuiwebapp"
export S2S_AUTHORISED_SERVICES="payment_app,ccpay_bubble,api_gw,ccd_gw,xui_webapp,ccpay_gw,pcs_api,pt_api"
export REFUND_SERVICE_ACCOUNT_CLIENT_ID="refunds_api"
export REFUND_SERVICE_ACCOUNT_GRANT_TYPE="password"
export REFUND_SERVICE_ACCOUNT_USERNAME="idam.user.ccpayrefundsapi@hmcts.net"
export REFUND_SERVICE_ACCOUNT_SCOPE="openid profile roles search-user"
export REFUND_STATUS_UPDATE_PATH="/refunds-api/refund"
export USER_INFO_SIZE="300"
export USER_LAST_MODIFIED_TIME="720d"
export LAUNCH_DARKLY_USER_NAME_PREFIX="${ENVIRONMENT}"
export POSTGRES_NAME="refunds"
export POSTGRES_CONNECTION_OPTIONS="?sslmode=require"
export SPRING_LIQUIBASE_ENABLED="false"
export LIBERATA_OAUTH2_AUTHORIZE_URL="https://bpacustomerportal.liberata.com/pba/public/oauth/authorize"
export LIBERATA_OAUTH2_TOKEN_URL="https://bpacustomerportal.liberata.com/pba/public/oauth/token"
export LIBERATA_BASE_URL="https://bpacustomerportal.liberata.com"

# Jenkins agents run in UTC; enforce the same on the local JVM.
export TZ=UTC
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Duser.timezone=UTC"
export JAVA_TOOL_OPTIONS

usage() {
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

if [[ "${ENVIRONMENT}" == "-h" || "${ENVIRONMENT}" == "--help" ]]; then
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

if ! az keyvault secret show --vault-name "$VAULT" --name refunds-s2s-secret \
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
# Load secrets from Key Vault (mapping from values.yaml keyVaults block)
# ---------------------------------------------------------------------------
require POSTGRES_HOST                          refunds-api-POSTGRES-HOST
require POSTGRES_PORT                          refunds-api-POSTGRES-PORT
require POSTGRES_USERNAME                      refunds-api-POSTGRES-USER
require POSTGRES_PASSWORD                      refunds-api-POSTGRES-PASS
require OIDC_S2S_SECRET                        refunds-s2s-secret
require OIDC_CLIENT_SECRET                     paybubble-idam-client-secret
require LAUNCH_DARKLY_SDK_KEY                  launch-darkly-sdk-key
require LIBERATA_OAUTH2_CLIENT_ID              liberata-keys-oauth2-client-id
require LIBERATA_OAUTH2_CLIENT_SECRET          liberata-keys-oauth2-client-secret
require LIBERATA_OAUTH2_USERNAME               liberata-keys-oauth2-username
require LIBERATA_OAUTH2_PASSWORD               liberata-keys-oauth2-password
require LIBERATA_API_KEY                       liberata-api-key
require REFUND_SERVICE_ACCOUNT_PASSWORD        refunds-api-user-password
require REFUND_SERVICE_ACCOUNT_CLIENT_SECRET   refunds-api-client-secret
require LIBERATA_USERNAME                      ccpay-liberata-user-id
require LIBERATA_USER_PASSWORD                 ccpay-liberata-user-password
require NOTIFY_API_KEY                         notifications-email-apikey
require NOTIFICATION_LETTER_TEMPLATE_ID        notifications-letter-template-id
require NOTIFICATION_EMAIL_TEMPLATE_ID         notifications-email-template-id
require NOTIFICATIONS_LETTER_CHEQUE_PO_CASH_TEMPLATE_ID  notifications-letter-cheque-po-cash-template-id
require NOTIFICATIONS_EMAIL_CHEQUE_PO_CASH_TEMPLATE_ID  notifications-email-cheque-po-cash-template-id
require NOTIFICATION_LETTER_CARD_PBA_TEMPLATE_ID        notifications-letter-card-pba-template-id
require NOTIFICATION_EMAIL_CARD_PBA_TEMPLATE_ID         notifications-email-card-pba-template-id
require NOTIFICATION_LETTER_REFUND_WHEN_CONTACTED_TEMPLATE_ID  notifications-letter-refund-when-contacted-template-id
require NOTIFICATION_EMAIL_REFUND_WHEN_CONTACTED_TEMPLATE_ID  notifications-email-refund-when-contacted-template-id

# Sanity check the secrets the app needs most
for env_var in POSTGRES_HOST POSTGRES_USERNAME POSTGRES_PASSWORD OIDC_S2S_SECRET OIDC_CLIENT_SECRET; do
    if [ -z "${!env_var:-}" ]; then
        echo "ERROR: required secret '$env_var' is empty - cannot run the application." >&2
        exit 1
    fi
done

# ---------------------------------------------------------------------------
# Run the application
# ---------------------------------------------------------------------------
echo "Running Refunds API against environment: $ENVIRONMENT"
./gradlew bootRun
