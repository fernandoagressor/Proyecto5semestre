#!/usr/bin/env sh
# Usuario de solo lectura para mysqld-exporter (monitoreo, compose.monitoring.yaml).
# Sin MYSQL_EXPORTER_PASSWORD (desarrollo) no hace nada. Solo corre al crear la base; en una
# base existente cree el usuario con el comando de docs/DESPLIEGUE_AWS.md.

if [ -n "${MYSQL_EXPORTER_PASSWORD:-}" ]; then
  mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}' WITH MAX_USER_CONNECTIONS 3;
ALTER USER 'exporter'@'%' IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
EOSQL
fi
