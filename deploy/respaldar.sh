#!/usr/bin/env bash
# Respaldo de NEXORA CORE: MySQL, MongoDB y los documentos subidos, copiados a S3.
#
#   cd /opt/nexora && bash deploy/respaldar.sh
#
# Lo corre cron todos los días (deploy/instalar-respaldos.sh) y deploy/desplegar.sh antes de
# cada despliegue. Cada respaldo queda en $RESPALDO_DIR/nexora-AAAAMMDD-HHMMSS/:
#   mysql.sql.gz         mysqldump en una transacción consistente, con triggers y rutinas
#   mongodb.archive      mongodump de la base de la aplicación (comprimido por dentro)
#   documentos.tar.gz    archivos subidos (volumen "documentos")
#   MANIFIESTO.txt       versión, migración de Flyway, número de tablas y colecciones
#   SHA256SUMS           para detectar archivos dañados antes de restaurar
# Se sube a s3://$RESPALDO_S3_BUCKET/respaldos/ y las copias locales de más de
# RESPALDO_DIAS_LOCAL días (3) se borran. Sin RESPALDO_S3_BUCKET queda solo la copia local.
# Redis no se respalda: guarda solo caché y contadores de intentos.
#
# Restaurar: deploy/restaurar.sh. Probar sin tocar nada: deploy/probar-restauracion.sh.

set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/respaldos-comun.sh

DIAS_LOCAL="$(leer_env RESPALDO_DIAS_LOCAL)"; DIAS_LOCAL="${DIAS_LOCAL:-3}"
mkdir -p "$RESPALDO_DIR" 2>/dev/null || error "No se puede crear $RESPALDO_DIR (ver docs/DESPLIEGUE_AWS.md, Respaldos)"

exec 9> "$RESPALDO_DIR/.bloqueo"
flock -n 9 || error "Ya hay un respaldo en curso"

for s in mysql mongodb; do
  [[ -n "$(docker compose ps --status running -q "$s")" ]] || error "El servicio $s no está corriendo"
done

nombre="nexora-$(date +%Y%m%d-%H%M%S)"
parcial="$RESPALDO_DIR/$nombre.parcial"
destino="$RESPALDO_DIR/$nombre"
inicio="$(date +%s)"
mkdir -p "$parcial"
trap 'rm -rf "$parcial"' EXIT

registro "Respaldo $nombre"

registro "MySQL"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
    --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF \
    --no-tablespaces --add-drop-database --databases "$MYSQL_DATABASE"' \
  | gzip -6 > "$parcial/mysql.sql.gz"
gzip -dc "$parcial/mysql.sql.gz" | tail -1 | grep -q 'Dump completed' || error "El volcado de MySQL quedó incompleto"

registro "MongoDB"
docker compose exec -T mongodb sh -c 'exec mongodump --quiet \
    -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin \
    --db "$MONGO_APP_DATABASE" --archive --gzip' > "$parcial/mongodb.archive"

registro "Documentos"
if [[ -n "$(docker compose ps --status running -q api)" ]]; then
  docker compose exec -T api tar -czf - -C /app/storage . > "$parcial/documentos.tar.gz"
else
  docker compose run --rm --no-deps -T --entrypoint tar api -czf - -C /app/storage . > "$parcial/documentos.tar.gz"
fi

for f in "$parcial"/mysql.sql.gz "$parcial"/documentos.tar.gz; do gzip -t "$f" || error "$(basename "$f") quedó dañado"; done

bd="$(docker compose exec -T mysql printenv MYSQL_DATABASE | tr -d '\r')"
# En una base recién creada (antes del primer arranque de la API) aún no existen las tablas.
flyway="$(mysql_root -N -s -e "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1" "$bd" 2>/dev/null || echo ninguna)"
tablas="$(mysql_root -N -s -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$bd'")"
usuarios="$(mysql_root -N -s -e "SELECT COUNT(*) FROM usuarios" "$bd" 2>/dev/null || echo 0)"
mongo_bd="$(docker compose exec -T mongodb printenv MONGO_APP_DATABASE | tr -d '\r')"
colecciones="$(mongo_root 'db.getCollectionNames().length')"
archivos="$(tar -tzf "$parcial/documentos.tar.gz" | grep -vc '/$' || true)"

cat > "$parcial/MANIFIESTO.txt" <<EOF
nombre=$nombre
fecha=$(date -Iseconds)
servidor=$(hostname)
version=$(leer_env NEXORA_VERSION)
flyway=$flyway
mysql_base=$bd
mysql_tablas=$tablas
mysql_usuarios=$usuarios
mongodb_base=$mongo_bd
mongodb_colecciones=$colecciones
documentos_archivos=$archivos
EOF
(cd "$parcial" && sha256sum mysql.sql.gz mongodb.archive documentos.tar.gz MANIFIESTO.txt > SHA256SUMS)

mv "$parcial" "$destino"
trap - EXIT
bytes="$(du -sb "$destino" | cut -f1)"
registro "Local: $destino ($(du -sh "$destino" | cut -f1); Flyway V$flyway, $tablas tablas, $colecciones colecciones, $archivos documentos)"

en_s3=0
if [[ -n "$RESPALDO_S3_BUCKET" ]]; then
  command -v aws > /dev/null || error "Falta la AWS CLI (sudo snap install aws-cli --classic)"
  url="s3://$RESPALDO_S3_BUCKET/respaldos/$nombre/"
  registro "Subiendo a $url"
  aws s3 cp --recursive --only-show-errors "$destino/" "$url" || error "No se pudo subir a $url"
  subidos="$(aws s3 ls "$url" | wc -l)"
  [[ "$subidos" -ge 5 ]] || error "En $url hay $subidos archivos, se esperaban 5"
  en_s3=1
else
  registro "Sin RESPALDO_S3_BUCKET: el respaldo queda solo en este servidor"
fi

find "$RESPALDO_DIR" -mindepth 1 -maxdepth 1 -type d \( -name 'nexora-*' -o -name 'descargado-*' \) \
  -mtime +"$DIAS_LOCAL" -exec rm -rf {} +

fin="$(date +%s)"
publicar_metricas respaldo.prom "# HELP nexora_respaldo_ultimo_exito_timestamp_seconds Hora del último respaldo completo.
# TYPE nexora_respaldo_ultimo_exito_timestamp_seconds gauge
nexora_respaldo_ultimo_exito_timestamp_seconds $fin
# HELP nexora_respaldo_bytes Tamaño del último respaldo.
# TYPE nexora_respaldo_bytes gauge
nexora_respaldo_bytes $bytes
# HELP nexora_respaldo_duracion_segundos Duración del último respaldo.
# TYPE nexora_respaldo_duracion_segundos gauge
nexora_respaldo_duracion_segundos $((fin - inicio))
# HELP nexora_respaldo_en_s3 1 si el último respaldo se subió a S3.
# TYPE nexora_respaldo_en_s3 gauge
nexora_respaldo_en_s3 $en_s3"

registro "Respaldo $nombre listo en $((fin - inicio)) s"
