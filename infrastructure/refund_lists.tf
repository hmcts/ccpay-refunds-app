module "ccpay-refund-lists-product" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg
  name = var.product_name
  product_access_control_groups = ["developers"]
}

module "ccpay-refund-lists-api" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg
  revision      = "1"
  service_url   = local.refunds_api_url
  product_id    = module.ccpay-refund-lists-product.product_id
  name          = join("-", [var.product_name, "api"])
  display_name  = "Refund List API"
  path          = "refunds-api"
  swagger_url   = "https://raw.githubusercontent.com/hmcts/reform-api-docs/master/docs/specs/ccpay-payment-app.refunds-status.json"
}

data "template_file" "refund_lists_policy_template" {
  template = file(join("", [path.module, "/template/api-policy.xml"]))

  vars = {
    allowed_certificate_thumbprints = local.refund_status_thumbprints_in_quotes_str
    s2s_client_id                   = data.azurerm_key_vault_secret.s2s_client_id.value
    s2s_client_secret               = data.azurerm_key_vault_secret.s2s_client_secret.value
    s2s_base_url                    = local.s2sUrl
  }
}

module "ccpay-refund-lists-policy" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api-policy?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg

  api_name               = module.ccpay-refund-lists-api.name
  api_policy_xml_content = data.template_file.refund_status_policy_template.rendered
}



resource "azurerm_api_management_user" "refund_list_user" {
  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg
  user_id             = "5931a75ae4bbd512288c680b"
  first_name          = "Anshika"
  last_name           = "Nigam"
  email               = "anshika.nigam@hmcts.net"
  state               = "active"
}

resource "azurerm_api_management_subscription" "refund_lists_subscription" {
  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg
  user_id             = azurerm_api_management_user.refund_list_user.id
  product_id          = module.ccpay-refund-lists-api.product_id
  display_name        = "Test Subscription"
  state               = "active"
}


