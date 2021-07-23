variable "product" {
  type    = string
  default = "ccpay"
}

variable "component" {
  type    = string
  default = "refunds-api"

}

variable "team_name" {
  type    = string
  default = "FeesAndPay"

}

variable "location" {
  type    = string
  default = "UK South"
}

variable "env" {
  type = string
}

variable "subscription" {
  type    = string
}

variable "common_tags" {
  type = map(string)
}

variable "core_product" {
  type    = string
  default = "ccpay"
}

variable "postgresql_user" {
  type    = string
  default = "refunds"
}

variable "database_name" {
  type    = string
  default = "refunds"
}

variable sku_name {
  default = "GP_Gen5_2"
}

variable "sku_capacity" {
  default = "2"
}

variable "postgresql_version" {
  default = "11"
}
