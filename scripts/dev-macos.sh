#!/bin/bash
set -euo pipefail

# macOS/Apple Silicon development bootstrap for InkNexus.
# Starts Docker Desktop if needed, brings up local middleware, waits for
# health checks, publishes Nacos config, and creates the Sentinel log dir.

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

log() {
  printf '\n[dev-macos] %s\n' "$*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

ensure_docker() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi

  if [ -d /Applications/Docker.app ]; then
    log "Docker daemon is not running. Starting Docker Desktop..."
    open -a Docker
  fi

  log "Waiting for Docker daemon..."
  for _ in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Docker did not become ready in time." >&2
  exit 1
}

wait_for_container_health() {
  local container="$1"
  for _ in $(seq 1 60); do
    local status
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo starting)"
    if [ "$status" = "healthy" ]; then
      return 0
    fi
    if [ "$status" = "none" ]; then
      log "Container $container has no healthcheck, treating as ready."
      return 0
    fi
    sleep 2
  done

  echo "Container $container did not become healthy in time." >&2
  docker ps --filter "name=^/${container}$" --format '{{.Names}} {{.Status}}' >&2 || true
  exit 1
}

require_cmd docker
require_cmd curl
require_cmd java
require_cmd mvn

arch="$(uname -m)"
log "Host architecture: $arch"
if [ "$arch" != "arm64" ]; then
  log "This bootstrap targets Apple Silicon; continuing with host architecture $arch."
fi

ensure_docker

docker_arch="$(docker info --format '{{.Architecture}}')"
log "Docker architecture: $docker_arch"

log "Starting local middleware..."
docker compose -f docker-compose.infra.yml up -d

for container in mysql nacos redis rabbitmq; do
  wait_for_container_health "$container"
done

log "Publishing Nacos configuration..."
bash nacos-config/publish.sh

mkdir -p logs/sentinel

log "Local middleware is ready."
cat <<'EOF'

Next steps:

  Backend, in order:
    mvn -f InkNexus/pom.xml -pl inknexus-auth spring-boot:run
    mvn -f InkNexus/pom.xml -pl inknexus-book spring-boot:run "-Dspring-boot.run.jvmArguments=-Dcsp.sentinel.log.dir=${PWD}/logs/sentinel"
    mvn -f InkNexus/pom.xml -pl inknexus-cart spring-boot:run
    mvn -f InkNexus/pom.xml -pl inknexus-stock spring-boot:run
    mvn -f InkNexus/pom.xml -pl inknexus-order spring-boot:run
    mvn -f InkNexus/pom.xml -pl inknexus-payment spring-boot:run
    mvn -f InkNexus/pom.xml -pl inknexus-gateway spring-boot:run

  Frontend:
    cd front
    npm install
    npm run dev

  Stop middleware:
    docker compose -f docker-compose.infra.yml down
EOF
