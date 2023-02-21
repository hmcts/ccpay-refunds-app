module "ccpay-refund-status-product" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"

  api_mgmt_name                 = local.api_mgmt_name
  api_mgmt_rg                   = local.api_mgmt_rg
  name                          = var.product_name
  product_access_control_groups = ["developers"]
}

module "ccpay-refund-status-api" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"

  api_mgmt_name = local.api_mgmt_name
  api_mgmt_rg   = local.api_mgmt_rg
  revision      = "1"
  service_url   = local.refunds_api_url
  product_id    = module.ccpay-refund-status-product.product_id
  name          = join("-", [var.product_name, "api"])
  display_name  = "Refund Status API"
  path          = "refunds-api"
  swagger_url   = "https://raw.githubusercontent.com/hmcts/reform-api-docs/master/docs/specs/ccpay-payment-app.refunds-status-v1.json"
}

data "template_file" "refund_status_policy_template" {
  template = file(join("", [path.module, "/template/api-policy.xml"]))

  vars = {
    allowed_certificate_thumbprints = local.refund_status_thumbprints_in_quotes_str
    s2s_client_id                   = data.azurerm_key_vault_secret.s2s_client_id.value
    s2s_client_secret               = data.azurerm_key_vault_secret.s2s_client_secret.value
    s2s_base_url                    = local.s2sUrl
  }
}

module "ccpay-refund-status-policy" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api-policy?ref=master"

  api_mgmt_name = local.api_mgmt_name
  api_mgmt_rg   = local.api_mgmt_rg

  api_name               = module.ccpay-refund-status-api.name
  api_policy_xml_content = data.template_file.refund_status_policy_template.rendered
}


data "azurerm_api_management_user" "refund_user" {
  user_id             = "5731a75ae4bcd512288c690e"
  api_management_name = local.api_mgmt_name
  resource_group_name = local.api_mgmt_rg
}


resource "azurerm_api_management_subscription" "refund_subs" {
  api_management_name = local.api_mgmt_name
  resource_group_name = local.api_mgmt_rg
  user_id             = data.azurerm_api_management_user.refund_user.id
  product_id          = module.ccpay-refund-status-product.id
  display_name        = "Refund Subscription"
  state               = "active"
}

data "azurerm_api_management_user" "refund_shravan" {
  user_id             = "628e5a527aa7530ff80227b2"
  api_management_name = local.api_mgmt_name
  resource_group_name = local.api_mgmt_rg
}

resource "azurerm_api_management_subscription" "refunds_subscription_shravan" {
  api_management_name = local.api_mgmt_name
  resource_group_name = local.api_mgmt_rg
  user_id             = data.azurerm_api_management_user.refund_shravan.id
  product_id          = module.ccpay-refund-status-product.id
  display_name        = "refunds"
  state               = "active"
}


