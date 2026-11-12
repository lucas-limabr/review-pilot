#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Arquivo .env nao encontrado na raiz do projeto." >&2
  echo "Copie .env.example para .env e preencha os segredos locais antes de iniciar:" >&2
  echo "  cp .env.example .env" >&2
  exit 1
fi

REQUIRED_KEYS=(
  "POSTGRES_PASSWORD"
  "INTERNAL_SERVICE_TOKEN"
  "GITHUB_WEBHOOK_SECRET"
  "N8N_ENCRYPTION_KEY"
)

for key in "${REQUIRED_KEYS[@]}"; do
  val="$(grep -E "^${key}=" "${ENV_FILE}" | head -n 1 | cut -d'=' -f2- || true)"
  if [[ -z "${val}" || "${val}" == "CHANGE_ME_LOCALLY" ]]; then
    echo "[ERROR] A variavel obrigatoria ${key} nao foi configurada ou permanece com placeholder CHANGE_ME_LOCALLY no .env." >&2
    echo "Gere um valor seguro com: openssl rand -hex 32" >&2
    exit 1
  fi
done

COMPOSE_ARGS=()
for arg in "$@"; do
  case "${arg}" in
    --quality)
      COMPOSE_ARGS+=("--profile" "quality")
      ;;
    --full)
      COMPOSE_ARGS+=("--profile" "full")
      ;;
    *)
      COMPOSE_ARGS+=("${arg}")
      ;;
  esac
done

cd "${ROOT_DIR}"
docker compose "${COMPOSE_ARGS[@]}" up -d
docker compose "${COMPOSE_ARGS[@]}" ps

