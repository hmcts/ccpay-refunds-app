locals {
  api_mgmt_name_cft     = "cft-api-mgmt-${var.env}"
  api_mgmt_rg_cft      = "cft-${var.env}-network-rg"
  api_base_path_cft    = var.product
  gateway_client_id = "api_gw"
}


module "api_mgmt_product" {
  source        = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"
  name          = var.product_name
  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft
}

module "api_mgmt_api" {
  source        = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"
  name          = join("-", [var.product_name, "api"])
  display_name  = "Refund List API"
  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft
  product_id    = module.api_mgmt_product.product_id
  path          = "refunds-api"
  service_url   = local.refunds_api_url
  swagger_url   = "https://raw.githubusercontent.com/hmcts/reform-api-docs/master/docs/specs/ccpay-payment-app.refunds-list.json"
  revision      = "1"
}

data "template_file" "refund_list_policy_template" {
  template = file(join("", [path.module, "/template/api-policy.xml"]))

  vars = {
    allowed_certificate_thumbprints = local.refund_status_thumbprints_in_quotes_str
    s2s_client_id                   = data.azurerm_key_vault_secret.s2s_client_id.value
    s2s_client_secret               = data.azurerm_key_vault_secret.s2s_client_secret.value
    s2s_base_url                    = local.s2sUrl
  }
}

module "ccpay-refund-list-policy" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api-policy?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft

  api_name               = module.api_mgmt_api.name
  api_policy_xml_content = data.template_file.refund_list_policy_template.rendered
}

