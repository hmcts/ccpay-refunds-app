# Subscription keys for the CFT APIM

# Internal subscription - Fee and Payment DTS Team
resource "azurerm_api_management_subscription" "fee_pay_team_refund_subscription" {
  api_management_name = local.cft_api_mgmt_name
  resource_group_name = local.cft_api_mgmt_rg
  product_id          = module.cft_api_mgmt_product.id
  display_name        = "Refund Status API - Fee and Pay DTS Team Subscription"
  state               = "active"
  provider            = azurerm.aks-cftapps
}

resource "azurerm_key_vault_secret" "fee_pay_team_refund_subscription_key" {
  name         = "fee-pay-team-refund-cft-apim-subscription-key"
  value        = azurerm_api_management_subscription.fee_pay_team_refund_subscription.primary_key
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}

# Supplier subscription - Liberata
resource "azurerm_api_management_subscription" "liberata_supplier_subscription" {
  api_management_name = local.cft_api_mgmt_name
  resource_group_name = local.cft_api_mgmt_rg
  product_id          = module.cft_api_mgmt_product.id
  display_name        = "Refund Status API - Liberata Subscription"
  state               = "active"
  provider            = azurerm.aks-cftapps
  primary_key         = data.azurerm_key_vault_secret.liberata_supplier_subscription_key.value

  depends_on = [data.azurerm_key_vault_secret.liberata_supplier_refund_subscription_key]
}

data "azurerm_key_vault_secret" "liberata_supplier_refund_subscription_key" {
  name         = "liberata-cft-apim-refund-subscription-key"
  key_vault_id = data.azurerm_key_vault.refunds_key_vault.id
}
