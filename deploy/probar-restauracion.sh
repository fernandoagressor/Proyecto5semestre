#!/usr/bin/env bash
# Prueba que un respaldo se puede restaurar, sin tocar los datos del servidor.
#
#   cd /opt/nexora && bash deploy/probar-restauracion.sh              # el último respaldo local
#   bash deploy/probar-restauracion.sh s3://<bucket>/respaldos/nexora-...   # uno de S3
#
# Levanta un MySQL y un MongoDB temporales (sin puertos, en una red aparte), carga el respaldo y
# compara con el MANIFIESTO: migración de Flyway, número de tablas y colecciones y usuarios.
# Revisa también que el archivo de documentos se pueda leer. Al terminar borra los temporales.
# Lo corre cron cada domingo (deploy/instalar-respaldos.sh).

set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/respaldos-comun.sh

origen="${1:-$(ultimo_respaldo_local)}"
[[ -n "$origen" ]] || error "No hay respaldos en $RESPALDO_DIR"
dir="$(obtener_respaldo "$origen")"
verificar_integridad "$dir"
nombre="$(manifiesto "$dir" nombre)"
registro "Probando la restauración de $nombre"

imagen() { docker compose config --format json | jq -r ".services.$1.image"; }
id="nexora-prueba-restauracion-$$"
clave="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
limpiar() {
  docker rm -f "$id-mysql" "$id-mongo" > /dev/null 2>&1 || true
  docker network rm "$id" > /dev/null 2>&1 || true
  [[ "$origen" == s3://* ]] && rm -rf "$dir"
  return 0
}
trap limpiar EXIT

docker network create --internal "$id" > /dev/null
docker run -d --name "$id-mysql" --network "$id" --memory 2g -e MYSQL_ROOT_PASSWORD="$clave" \
  "$(imagen mysql)" --log-bin-trust-function-creators=1 --innodb-buffer-pool-size=256M > /dev/null
docker run -d --name "$id-mongo" --network "$id" --memory 2g -e GLIBC_TUNABLES=glibc.pthread.rseq=1 \
  "$(imagen mongodb)" --wiredTigerCacheSizeGB 0.25 > /dev/null

my() { docker exec -i -e MYSQL_PWD="$clave" "$id-mysql" mysql -uroot -h127.0.0.1 "$@"; }
listo=0
for _ in $(seq 90); do my -e 'SELECT 1' > /dev/null 2>&1 && { listo=1; break; }; sleep 2; done
[[ "$listo" == 1 ]] || error "El MySQL temporal no arrancó"
listo=0
for _ in $(seq 60); do
  docker exec "$id-mongo" mongosh --quiet --eval 'db.hello().isWritablePrimary' 2>/dev/null | grep -q true && { listo=1; break; }
  sleep 2
done
[[ "$listo" == 1 ]] || error "El MongoDB temporal no arrancó"

registro "Cargando MySQL"
gzip -dc "$dir/mysql.sql.gz" | my || error "El volcado de MySQL no se pudo cargar"
registro "Cargando MongoDB"
docker exec -i "$id-mongo" mongorestore --quiet --archive --gzip < "$dir/mongodb.archive" \
  || error "El volcado de MongoDB no se pudo cargar"
registro "Leyendo documentos"
archivos="$(tar -tzf "$dir/documentos.tar.gz" | grep -vc '/$' || true)"

bd="$(manifiesto "$dir" mysql_base)"
mongo_bd="$(manifiesto "$dir" mongodb_base)"
flyway="$(my -N -s -e "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1" "$bd")"
tablas="$(my -N -s -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$bd'")"
usuarios="$(my -N -s -e "SELECT COUNT(*) FROM usuarios" "$bd")"
colecciones="$(docker exec "$id-mongo" mongosh --quiet "$mongo_bd" --eval 'db.getCollectionNames().length' | tail -1)"
documentos_mongo="$(docker exec "$id-mongo" mongosh --quiet "$mongo_bd" --eval \
  'db.getCollectionNames().reduce((n, c) => n + db.getCollection(c).estimatedDocumentCount(), 0)' | tail -1)"

fallos=0
comparar() {
  if [[ "$2" == "$3" ]]; then registro "  ok  $1: $2"
  else registro "  MAL $1: restaurado $2, esperado $3"; fallos=$((fallos + 1)); fi
}
comparar "Flyway" "$flyway" "$(manifiesto "$dir" flyway)"
comparar "Tablas de MySQL" "$tablas" "$(manifiesto "$dir" mysql_tablas)"
comparar "Usuarios" "$usuarios" "$(manifiesto "$dir" mysql_usuarios)"
comparar "Colecciones de MongoDB" "$colecciones" "$(manifiesto "$dir" mongodb_colecciones)"
comparar "Documentos subidos" "$archivos" "$(manifiesto "$dir" documentos_archivos)"
[[ "$usuarios" -gt 0 ]] || { registro "  MAL la tabla usuarios quedó vacía"; fallos=$((fallos + 1)); }
registro "  MongoDB tiene $documentos_mongo documentos en total"

[[ "$fallos" == 0 ]] || error "La restauración de $nombre no coincide con su manifiesto ($fallos diferencias)"

publicar_metricas restauracion.prom "# HELP nexora_respaldo_prueba_ultimo_exito_timestamp_seconds Hora de la última prueba de restauración superada.
# TYPE nexora_respaldo_prueba_ultimo_exito_timestamp_seconds gauge
nexora_respaldo_prueba_ultimo_exito_timestamp_seconds $(date +%s)"
registro "Prueba de restauración superada: $nombre se puede restaurar"
