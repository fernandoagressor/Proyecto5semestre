#!/usr/bin/env sh

set -eu

ENV_FILE="${1:-.env}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker no está instalado o no está disponible en PATH." >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: No existe $ENV_FILE. Copie .env.example a .env y cambie las credenciales." >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ENV_FILE" "$@"
}

echo "[1/8] Validando la configuración de Docker Compose"
compose config --quiet

echo "[2/8] Verificando MySQL con el usuario de aplicación"
compose exec -T mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D"$MYSQL_DATABASE" -Nse "SELECT 1"' \
  | grep -qx '1'

echo "[3/8] Verificando el usuario de MySQL para Relational Migrator"
compose exec -T mysql sh -c \
  'mysql -u"$MYSQL_MIGRATOR_USER" -p"$MYSQL_MIGRATOR_PASSWORD" -D"$MYSQL_DATABASE" -Nse "SELECT 1"' \
  | grep -qx '1'

echo "[4/8] Verificando MongoDB con el usuario de aplicación"
compose exec -T mongodb sh -c \
  'mongosh --quiet --username "$MONGO_APP_USER" --password "$MONGO_APP_PASSWORD" --authenticationDatabase "$MONGO_APP_DATABASE" "$MONGO_APP_DATABASE" --eval "quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)"'

echo "[5/8] Verificando el usuario de MongoDB para Relational Migrator"
compose exec -T mongodb sh -c \
  'mongosh --quiet --username "$MONGO_MIGRATOR_USER" --password "$MONGO_MIGRATOR_PASSWORD" --authenticationDatabase admin "$MONGO_APP_DATABASE" --eval "quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)"'

echo "[6/8] Verificando Redis autenticado"
compose exec -T redis sh -c \
  'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping' \
  | grep -qx 'PONG'

echo "[7/8] Verificando que Spring Boot vea las tres bases"
compose exec -T api sh -c \
  'wget -q -O - http://localhost:8080/actuator/health/readiness' \
  | grep -q '"status":"UP"'

echo "[8/8] Verificando el frontend"
compose exec -T frontend sh -c \
  'wget -q -O - http://localhost/healthz' \
  | grep -q 'ok'

echo "OK: MySQL, MongoDB, Redis, API y frontend están disponibles."
