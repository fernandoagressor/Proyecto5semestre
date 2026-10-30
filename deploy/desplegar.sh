#!/usr/bin/env bash
# Despliega una versión de NEXORA CORE en este servidor (lo llama el workflow "Desplegar" por SSH,
# o un operador a mano):
#
#   cd /opt/nexora && bash deploy/desplegar.sh <versión>     # p. ej. 0.4.0 o sha-<commit>
#
# Saca un respaldo (deploy/respaldar.sh), fija NEXORA_VERSION en .env, baja las imágenes de GHCR y reinicia solo lo que cambió. Si la
# versión nueva no queda sana en 10 minutos, vuelve a la anterior y termina con error.
# Ojo: las migraciones Flyway que la versión nueva alcanzó a aplicar no se deshacen.

set -euo pipefail

VERSION="${1:?Uso: deploy/desplegar.sh <versión>}"
[[ "$VERSION" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Versión inválida: $VERSION" >&2; exit 1; }
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "Falta .env en $(pwd) (ver deploy/produccion.env.example)" >&2; exit 1; }

grep -q '^COMPOSE_FILE=' .env || echo 'COMPOSE_FILE=compose.prod.yaml:compose.monitoring.yaml' >> .env

fijar_version() {
  if grep -q '^NEXORA_VERSION=' .env; then
    sed -i "s/^NEXORA_VERSION=.*/NEXORA_VERSION=$1/" .env
  else
    echo "NEXORA_VERSION=$1" >> .env
  fi
}

anterior="$(grep '^NEXORA_VERSION=' .env | cut -d= -f2- || true)"
echo "Desplegando NEXORA $VERSION (antes: ${anterior:-ninguna})"

# Respaldo antes de desplegar: las migraciones de Flyway de la versión nueva no se deshacen.
# RESPALDO_ANTES_DE_DESPLEGAR=false en .env lo omite.
source deploy/respaldos-comun.sh
if [[ "$(leer_env RESPALDO_ANTES_DE_DESPLEGAR)" == false ]]; then
  echo "Respaldo previo omitido (RESPALDO_ANTES_DE_DESPLEGAR=false)"
elif [[ -z "$(docker compose ps --status running -q mysql 2>/dev/null)" ]]; then
  echo "Sin bases corriendo: no hay nada que respaldar"
elif [[ ! -d "$RESPALDO_DIR" ]]; then
  echo "AVISO: respaldos sin instalar ($RESPALDO_DIR no existe): bash deploy/instalar-respaldos.sh" >&2
else
  bash deploy/respaldar.sh || { echo "Falló el respaldo previo: no se despliega" >&2; exit 1; }
fi
fijar_version "$VERSION"

docker compose pull --quiet api frontend
if ! docker compose up -d --wait --wait-timeout 600; then
  echo "La versión $VERSION no quedó sana." >&2
  docker compose ps >&2
  docker compose logs --no-color --tail 80 api >&2 || true
  if [[ -n "$anterior" && "$anterior" != "$VERSION" ]]; then
    echo "Volviendo a $anterior" >&2
    fijar_version "$anterior"
    docker compose up -d --wait --wait-timeout 600 || true
  fi
  exit 1
fi

docker image prune -f --filter "until=168h" > /dev/null || true
docker compose ps
echo "NEXORA $VERSION desplegada"
