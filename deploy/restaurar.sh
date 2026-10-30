#!/usr/bin/env bash
# Restaura un respaldo de deploy/respaldar.sh en ESTE servidor. Reemplaza todos los datos.
#
#   cd /opt/nexora
#   bash deploy/restaurar.sh s3://<bucket>/respaldos/nexora-AAAAMMDD-HHMMSS --confirmar
#   bash deploy/restaurar.sh /var/lib/nexora/respaldos/nexora-AAAAMMDD-HHMMSS --confirmar
#
# Pasos: comprueba el sha256; saca un respaldo local de lo que hay ahora (se omite con
# --sin-respaldo-previo, p. ej. en un servidor nuevo); detiene la API y el frontend; carga MySQL,
# MongoDB y los documentos; borra la posición del Change Stream para que la API reconstruya las
# tablas espejo desde MongoDB; vacía la caché de Redis; levanta todo y espera que quede sano.
# La versión de la aplicación debe ser la del respaldo o una posterior (ver NEXORA_VERSION en el
# MANIFIESTO): una versión anterior no arranca con migraciones de Flyway más nuevas.

set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/respaldos-comun.sh

origen=""; confirmar=0; previo=1
for a in "$@"; do
  case "$a" in
    --confirmar) confirmar=1 ;;
    --sin-respaldo-previo) previo=0 ;;
    -*) error "Opción desconocida: $a" ;;
    *) origen="$a" ;;
  esac
done
[[ -n "$origen" ]] || error "Uso: deploy/restaurar.sh <carpeta | s3://...> --confirmar [--sin-respaldo-previo]"
[[ "$confirmar" == 1 ]] || error "Esto reemplaza todos los datos del servidor. Repita con --confirmar."

dir="$(obtener_respaldo "$origen")"
verificar_integridad "$dir"
registro "Restaurando $(manifiesto "$dir" nombre) (Flyway V$(manifiesto "$dir" flyway), versión $(manifiesto "$dir" version), de $(manifiesto "$dir" servidor))"

if [[ "$previo" == 1 ]]; then
  registro "Respaldo previo de lo que hay ahora (solo local)"
  RESPALDO_SOLO_LOCAL=1 bash deploy/respaldar.sh \
    || error "No se pudo sacar el respaldo previo; si el servidor es nuevo use --sin-respaldo-previo"
fi

registro "Deteniendo la API y el frontend"
docker compose stop frontend api
docker compose up -d --wait --wait-timeout 300 mysql mongodb redis

registro "MySQL"
gzip -dc "$dir/mysql.sql.gz" | mysql_root || error "Falló la carga de MySQL"

registro "MongoDB"
docker compose exec -T mongodb sh -c 'exec mongorestore --quiet \
    -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin \
    --nsInclude "$MONGO_APP_DATABASE.*" --drop --archive --gzip' < "$dir/mongodb.archive" \
  || error "Falló la carga de MongoDB"
# Sin posición guardada, la API reconstruye las tablas espejo desde MongoDB al arrancar.
mongo_root 'db.sync_tokens.deleteMany({}).deletedCount' > /dev/null

registro "Documentos"
docker compose run --rm --no-deps -T --entrypoint sh api \
  -c 'find /app/storage -mindepth 1 -delete && tar -xzf - -C /app/storage' < "$dir/documentos.tar.gz" \
  || error "Falló la restauración de los documentos"

registro "Vaciando la caché de Redis"
docker compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli FLUSHALL' > /dev/null

registro "Levantando todo"
docker compose up -d --wait --wait-timeout 600 || error "La aplicación no quedó sana después de restaurar"

[[ "$origen" == s3://* ]] && rm -rf "$dir"
registro "Restauración lista. Revise: bash scripts/prueba-humo.sh (ver docs/DESPLIEGUE_AWS.md)"
