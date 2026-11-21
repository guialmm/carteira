#!/bin/bash
# Runs on the server, started by GitHub Actions through SSM Run Command.
# Usage: deploy.sh <image> <domain>
set -euo pipefail

IMAGE="$1"
DOMAIN="$2"
APP_DIR=/opt/carteira
REGION=$(TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60') \
  && curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/region)
cd "$APP_DIR"

param() {
  aws ssm get-parameter --region "$REGION" --name "/carteira/$1" --with-decryption --query Parameter.Value --output text
}

# Secrets come from Parameter Store at deploy time and only live in this root-only file.
umask 077
cat > .env <<ENV
SPRING_DATASOURCE_URL=$(param db-url)
SPRING_DATASOURCE_USERNAME=$(param db-username)
SPRING_DATASOURCE_PASSWORD=$(param db-password)
CARTEIRA_JWT_SECRET=$(param jwt-secret)
ENV
printf 'IMAGE=%s\nDOMAIN=%s\n' "$IMAGE" "$DOMAIN" > compose.env

REGISTRY="${IMAGE%%/*}"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

docker compose --env-file compose.env -f compose.prod.yaml pull api
docker compose --env-file compose.env -f compose.prod.yaml up -d --remove-orphans

# Wait for the new API to answer before calling the deploy done.
for _ in $(seq 1 60); do
  if docker compose --env-file compose.env -f compose.prod.yaml exec -T api \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
    echo "API healthy"
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 3
done
echo "API did not become healthy" >&2
docker compose --env-file compose.env -f compose.prod.yaml logs --tail 80 api >&2
exit 1
