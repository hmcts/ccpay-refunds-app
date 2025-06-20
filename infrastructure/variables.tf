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
  type = string
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

variable "postgresql_flexible_sql_version" {
  default = "15"
}

variable "postgresql_flexible_server_port" {
  default = "5432"
}

variable "flexible_sku_name" {
  default = "GP_Standard_D2s_v3"
}

variable "aks_subscription_id" {}

variable "jenkins_AAD_objectId" {
  type        = string
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "refunds_api_gateway_certificate_thumbprints" {
  type    = list(any)
  default = [] # TODO: remove default and provide environment-specific values
}

variable "product_name" {
  type    = string
  default = "refunds"
}

variable "additional_databases" {
  default = []
}

variable "apim_suffix" {
  default = ""
}

variable "db_monitor_action_group_name" {
  description = "The name of the Action Group to create."
  type        = string
  default     = "db_monitor_ag"
}

variable "db_alert_email_address_key" {
  description = "Email address key in azure Key Vault."
  type        = string
  default     = "db-alert-monitoring-email-address"
}
