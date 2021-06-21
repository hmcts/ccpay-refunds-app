provider "azurerm" {
  features {}
}
locals {
  app_full_name = "${var.product}-${var.component}"
}

resource "azurerm_resource_group" "ccpay" {
  name      = join("-", [var.product, var.component, var.env])
  location  = var.location
  tags      = {
    "Deployment Environment"  = var.env
    "Team Name"               = var.team_name
    "lastUpdated"             = timestamp()
  }
}
