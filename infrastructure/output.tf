output "productID" {
  value = module.ccpay-refund-lists-product.product_id
}

output "product_ID" {
  value = data.azurerm_api_management_product.refundListApi.id
}