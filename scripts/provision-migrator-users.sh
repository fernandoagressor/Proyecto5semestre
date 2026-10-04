#!/usr/bin/env sh

set -eu

ENV_FILE="${1:-.env}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker no está instalado o no está disponible en PATH." >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: No existe $ENV_FILE." >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ENV_FILE" "$@"
}

echo "Configurando el usuario de Relational Migrator en MySQL"
compose exec -T mysql /docker-entrypoint-initdb.d/01-create-migrator-user.sh

echo "Configurando los usuarios de aplicación y migración en MongoDB"
compose exec -T mongodb sh -c \
  'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --file /docker-entrypoint-initdb.d/01-create-application-user.js'

echo "OK: usuarios de migración aprovisionados o actualizados."
