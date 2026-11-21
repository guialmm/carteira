# Secrets live in SSM Parameter Store (standard tier is free). The server reads them at deploy
# time; they never go to GitHub or into the image.
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "db_url" {
  name  = "/${var.project}/db-url"
  type  = "String"
  value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${aws_db_instance.main.db_name}?sslmode=require"
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project}/db-username"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/db-password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project}/jwt-secret"
  type  = "SecureString"
  value = random_password.jwt.result
}
