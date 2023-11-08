provider "azurerm" {
  features {}
}

locals {

  # Api Management config
  api_mgmt_name     = join("-", ["core-api-mgmt", var.env])
  api_mgmt_rg       = join("-", ["core-infra", var.env])

  vaultName = join("-", [var.core_product, var.env])
  s2sUrl = "http://rpe-service-auth-provider-${var.env}.service.core-compute-${var.env}.internal"
  s2s_rg_prefix               = "rpe-service-auth-provider"
  s2s_key_vault_name          = var.env == "preview" || var.env == "spreview" ? join("-", ["s2s", "aat"]) : join("-", ["s2s", var.env])
  s2s_vault_resource_group    = var.env == "preview" || var.env == "spreview" ? join("-", [local.s2s_rg_prefix, "aat"]) : join("-", [local.s2s_rg_prefix, var.env])
  refunds_api_url = join("", ["http://ccpay-refunds-api-", var.env, ".service.core-compute-", var.env, ".internal"])
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
  additional_databases = var.additional_databases
}

module "ccpay-refunds-database-v15" {
  providers = {
    azurerm.postgres_network = azurerm.postgres_network
  }
  source = "git@github.com:hmcts/terraform-module-postgresql-flexible?ref=master"
  product = var.product
  component = var.component
  business_area = "cft"
  name = join("-", [var.product, var.component, "postgres-db-v15"])
  location = var.location
  env = var.env
  pgsql_admin_username = var.postgresql_user
  pgsql_databases = [
      {
        name : var.database_name
      }
    ]
    pgsql_server_configuration = [
        {
          name  = "azure.extensions"
          value = "plpgsql,pg_stat_statements,pg_buffercache"
        }
      ]
  pgsql_sku = var.flexible_sku_name
  admin_user_object_id = var.jenkins_AAD_objectId
  common_tags = var.common_tags
  pgsql_version = var.postgresql_flexible_sql_version
}

# Populate Vault with DB info

resource "azurerm_key_vault_secret" "POSTGRES-USER" {
  name      = join("-", [var.component, "POSTGRES-USER"])
  value     = module.ccpay-refunds-database-v11.user_name
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS" {
  name      = join("-", [var.component, "POSTGRES-PASS"])
#   value     = module.ccpay-refunds-database-v11.postgresql_password
  value     = 'password'
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

# Populate Vault with Flexible DB info

resource "azurerm_key_vault_secret" "POSTGRES-USER-V15" {
  name      = join("-", [var.component, "POSTGRES-USER-V15"])
  value     = module.ccpay-refunds-database-v15.username
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS-V15" {
  name      = join("-", [var.component, "POSTGRES-PASS-V15"])
  value     = module.ccpay-refunds-database-v15.password
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_HOST-V15" {
  name      = join("-", [var.component, "POSTGRES-HOST-V15"])
  value     = module.ccpay-refunds-database-v15.fqdn
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_PORT-V15" {
  name      = join("-", [var.component, "POSTGRES-PORT-V15"])
  value     = var.postgresql_flexible_server_port
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_DATABASE-V15" {
  name      = join("-", [var.component, "POSTGRES-DATABASE-V15"])
  value     = var.database_name
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

data "azurerm_key_vault" "s2s_key_vault" {
  name                = local.s2s_key_vault_name
  resource_group_name = local.s2s_vault_resource_group
}

data "azurerm_key_vault_secret" "s2s_secret" {
  name          = "microservicekey-refunds-api"
  key_vault_id  = data.azurerm_key_vault.s2s_key_vault.id
}

resource "azurerm_key_vault_secret" "refunds_s2s_secret" {
  name          = "refunds-s2s-secret"
  value         = data.azurerm_key_vault_secret.s2s_secret.value
  key_vault_id  = data.azurerm_key_vault.refunds_key_vault.id
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
