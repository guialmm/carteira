locals {
  # sslip.io resolves 1-2-3-4.sslip.io to 1.2.3.4, which gives Caddy a hostname for a free
  # Let's Encrypt certificate without buying a domain.
  hostname = "${replace(aws_eip.server.public_ip, ".", "-")}.sslip.io"
}

output "api_url" {
  value = "https://${local.hostname}"
}

output "hostname" {
  value = local.hostname
}

output "instance_id" {
  value = aws_instance.server.id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
