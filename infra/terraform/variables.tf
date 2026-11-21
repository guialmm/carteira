variable "region" {
  description = "us-east-1 is the cheapest region; São Paulo costs roughly 50% more."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  type    = string
  default = "carteira"
}

variable "github_repository" {
  description = "owner/name of the repository allowed to deploy through OIDC."
  type        = string
  default     = "guialmm/carteira"
}

variable "instance_type" {
  description = "ARM (Graviton). 2 GB fits the API, RabbitMQ and Caddy."
  type        = string
  default     = "t4g.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "budget_limit_usd" {
  # On-demand in us-east-1 this stack costs about US$ 32/month: t4g.small ~12, db.t4g.micro ~12,
  # storage ~4, public IPv4 ~4. New-account credits cover it for months. The alarm sits a bit
  # above the expected bill, so an e-mail means something is off.
  description = "Monthly cost alarm. An e-mail goes out at 50% of real spend and when the forecast passes 100%."
  type        = number
  default     = 40
}

variable "budget_email" {
  description = "Where the cost alerts go. Set it in terraform.tfvars (not committed)."
  type        = string
}
