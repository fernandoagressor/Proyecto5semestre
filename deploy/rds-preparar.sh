#!/usr/bin/env bash
# Prepara la base de NEXORA en Amazon RDS (una sola vez, antes del primer despliegue):
#   - crea la base (utf8mb4) y el usuario de la aplicación con permisos solo sobre esa base;
#   - crea el usuario de solo lectura del monitoreo (mysqld-exporter);
#   - revisa los parámetros que necesita la aplicación.
#
#   cd /opt/nexora && bash deploy/rds-preparar.sh
#
# Usa el .env (RDS_HOST, RDS_ADMIN_USER, RDS_ADMIN_PASSWORD, DB_NAME, DB_USER, DB_PASSWORD,
# MYSQL_EXPORTER_PASSWORD) y compose.rds.yaml. Se puede correr varias veces: no borra nada.
# Las contraseñas viajan por la entrada estándar, no por la línea de comandos.
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/respaldos-comun.sh

grep -q 'compose.rds.yaml' <<<"$(leer_env COMPOSE_FILE)" || error "Agregue compose.rds.yaml a COMPOSE_FILE en el .env"
for v in RDS_HOST RDS_ADMIN_PASSWORD DB_PASSWORD MYSQL_EXPORTER_PASSWORD; do
  [[ -n "$(leer_env "$v")" ]] || error "Falta $v en el .env"
done
bd="$(leer_env DB_NAME)"; bd="${bd:-inmobiliaria_db}"
usuario="$(leer_env DB_USER)"; usuario="${usuario:-inmobiliaria}"
[[ "$bd" =~ ^[A-Za-z0-9_]+$ && "$usuario" =~ ^[A-Za-z0-9_]+$ ]] || error "DB_NAME y DB_USER solo admiten letras, números y _"
sql_texto() { printf '%s' "$1" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g"; }

registro "Conectando a RDS ($(leer_env RDS_HOST))"
docker compose up -d --wait --wait-timeout 300 mysql || error "El cliente de MySQL no llega a RDS: revise RDS_HOST, el grupo de seguridad y la contraseña"

registro "Base y usuarios"
mysql_root <<SQL || error "No se pudieron crear la base o los usuarios"
CREATE DATABASE IF NOT EXISTS \`$bd\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$usuario'@'%' IDENTIFIED BY '$(sql_texto "$(leer_env DB_PASSWORD)")';
ALTER USER '$usuario'@'%' IDENTIFIED BY '$(sql_texto "$(leer_env DB_PASSWORD)")';
GRANT ALL PRIVILEGES ON \`$bd\`.* TO '$usuario'@'%';
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '$(sql_texto "$(leer_env MYSQL_EXPORTER_PASSWORD)")' WITH MAX_USER_CONNECTIONS 3;
ALTER USER 'exporter'@'%' IDENTIFIED BY '$(sql_texto "$(leer_env MYSQL_EXPORTER_PASSWORD)")';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
SQL

registro "Parámetros"
confianza="$(mysql_root -N -s -e 'SELECT @@log_bin_trust_function_creators')"
zona="$(mysql_root -N -s -e 'SELECT @@time_zone')"
[[ "$confianza" == 1 ]] || error "En el grupo de parámetros de RDS ponga log_bin_trust_function_creators = 1 (las migraciones crean triggers) y reinicie la instancia"
registro "log_bin_trust_function_creators = 1; zona horaria de la base: $zona"
registro "RDS listo. Siga con: bash deploy/desplegar.sh <versión>"
