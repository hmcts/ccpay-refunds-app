provider "azurerm" {
  features {}
}

locals {

  # Api Management config
  api_mgmt_name     = join("-", ["core-api-mgmt", var.env])
  api_mgmt_rg       = join("-", ["core-infra", var.env])

  vaultName = join("-", [var.core_product, var.env])
  s2sUrl = "http://rpe-service-auth-provider-${var.env}.service.core-compute-${var.env}.internal"
  refunds_api_url = join("", ["http://refunds-api-", var.env, ".service.core-compute-", var.env, ".internal"])

  # list of the thumbprints of the SSL certificates that should be accepted by the refund status API (gateway)
  refund_status_thumbprints_in_quotes = formatlist("&quot;%s&quot;", var.refunds_api_gateway_certificate_thumbprints)
  refund_status_thumbprints_in_quotes_str = join(",", local.refund_status_thumbprints_in_quotes)
}

data "azurerm_key_vault" "refunds_key_vault" {
  name = "${local.vaultName}"
  resource_group_name = join("-", [var.core_product, var.env])
}

// Database Infra
module "ccpay-refunds-database-v11" {
  source = "git@github.com:hmcts/cnp-module-postgres?ref=master"
  product = var.product
  component = var.component
  name = "${var.product}-${var.component}-postgres-db-v11"
  location = var.location
  env = var.env
  postgresql_user = var.postgresql_user
  database_name = var.database_name
  sku_name = var.sku_name
  sku_capacity = var.sku_capacity
  sku_tier = "GeneralPurpose"
  common_tags = var.common_tags
  subscription = var.subscription
  postgresql_version = var.postgresql_version
}

# Populate Vault with DB info

resource "azurerm_key_vault_secret" "POSTGRES-USER" {
  name      = join("-", [var.component, "POSTGRES-USER"])
  value     = module.ccpay-refunds-database-v11.user_name
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS" {
  name      = join("-", [var.component, "POSTGRES-PASS"])
  value     = module.ccpay-refunds-database-v11.postgresql_password
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_HOST" {
  name      = join("-", [var.component, "POSTGRES-HOST"])
  value     = module.ccpay-refunds-database-v11.host_name
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_PORT" {
  name      = join("-", [var.component, "POSTGRES-PORT"])
  value     = module.ccpay-refunds-database-v11.postgresql_listen_port
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_DATABASE" {
  name      = join("-", [var.component, "POSTGRES-DATABASE"])
  value     = module.ccpay-refunds-database-v11.postgresql_database
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

data "azurerm_key_vault" "refund_key_vault" {
  name                = local.vaultName
  resource_group_name = local.vaultName
}

data "azurerm_key_vault_secret" "s2s_client_secret" {
  name         = "gateway-s2s-client-secret"
  key_vault_id = data.azurerm_key_vault.refund_key_vault.id
}

data "azurerm_key_vault_secret" "s2s_client_id" {
  name         = "gateway-s2s-client-id"
  key_vault_id = data.azurerm_key_vault.refund_key_vault.id
}

