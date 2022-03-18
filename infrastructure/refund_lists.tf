module "ccpay-refund-lists-product" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"
  provider            = azurerm.acmedcdcftapps
  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft
  name = "refundList"
  product_access_control_groups = ["developers"]
}

module "ccpay-refund-lists-api" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft
  revision      = "1"
  service_url   = local.refunds_api_url
  product_id    = module.ccpay-refund-lists-product.product_id
  name          = join("-", [var.product_name, "apiList"])
  display_name  = "Refund List API"
  path          = "refunds-api"
  swagger_url   = "https://raw.githubusercontent.com/hmcts/reform-api-docs/master/docs/specs/ccpay-payment-app.refunds-list.json"
}
 acmedcdcftapps = {
    demo = {
      subscription = "d025fece-ce99-4df2-b7a9-b649d3ff2060"
    }
  }
  
provider "azurerm" {
  alias                      = "acmedcdcftapps"
  skip_provider_registration = "true"
  features {}
  subscription_id = "d025fece-ce99-4df2-b7a9-b649d3ff2060"
}



