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

docker compose --env-file "$ENV_FILE" --profile migration config --quiet
docker compose --env-file "$ENV_FILE" --profile migration ps relational-migrator

MIGRATOR_ADDRESS="$(
  docker compose --env-file "$ENV_FILE" --profile migration \
    port relational-migrator 8080
)"
MIGRATOR_URL="http://${MIGRATOR_ADDRESS}"

attempt=1
while [ "$attempt" -le 30 ]; do
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS "$MIGRATOR_URL" >/dev/null 2>&1; then
      echo "OK: Relational Migrator responde en $MIGRATOR_URL"
      exit 0
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -q -O /dev/null "$MIGRATOR_URL"; then
      echo "OK: Relational Migrator responde en $MIGRATOR_URL"
      exit 0
    fi
  else
    echo "ERROR: instale curl o wget para ejecutar la prueba HTTP." >&2
    exit 1
  fi

  attempt=$((attempt + 1))
  sleep 2
done

echo "ERROR: Relational Migrator no respondió en $MIGRATOR_URL." >&2
exit 1
