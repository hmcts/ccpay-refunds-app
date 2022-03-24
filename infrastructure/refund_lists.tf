module "ccpay-refund-lists-product" {
  source                        = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"
  api_mgmt_name                 = local.api_mgmt_name_cft
  api_mgmt_rg                   = local.api_mgmt_rg_cft
  name                          = "refundListApi"
  product_access_control_groups = ["developers"]

  providers = {
    azurerm = azurerm.cftappsdemo
  }
}

module "ccpay-refund-lists-api" {
  source = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"

  api_mgmt_name = local.api_mgmt_name_cft
  api_mgmt_rg   = local.api_mgmt_rg_cft
  revision      = "1"
  service_url   = local.refunds_api_url
  product_id    = module.ccpay-refund-lists-product.product_id
  name          = join("-", [var.product_name, "apiLists"])
  display_name  = "Refund List API"
  path          = "refundslist-api"
  swagger_url   = "https://raw.githubusercontent.com/hmcts/reform-api-docs/master/docs/specs/ccpay-payment-app.refunds-list.json"

  providers = {
    azurerm = azurerm.cftappsdemo
  }
}
  

resource "azurerm_api_management_user" "refudList_user" {
  api_management_name = local.api_mgmt_name_cft
  resource_group_name = local.api_mgmt_rg_cft
  user_id             = "5931a75ae4bbd512288c680b"
  first_name          = "Anshika"
  last_name           = "Nigam"
  email               = "anshika.nigam@hmcts.net"
  state               = "active"

  provider = azurerm.cftappsdemo
}

data "azurerm_api_management_product" "refundListApi" {
  product_id          = module.ccpay-refund-lists-product.product_id
  api_management_name = local.api_mgmt_name_cft
  resource_group_name = local.api_mgmt_rg_cft

  provider = azurerm.cftappsdemo
}

resource "azurerm_api_management_subscription" "refudList_subscription" {
  api_management_name = local.api_mgmt_name_cft
  resource_group_name = local.api_mgmt_rg_cft
  user_id             = azurerm_api_management_user.refudList_user.id
  product_id          = data.azurerm_api_management_product.refundListApi.id
  display_name        = "RefudList Subscription"
  state               = "active"

  provider = azurerm.cftappsdemo
}


