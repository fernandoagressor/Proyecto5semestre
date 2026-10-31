#!/usr/bin/env sh
# Usuario de solo lectura para Relational Migrator. Debe ser EJECUTABLE (100755 en git): si no,
# la imagen de MySQL lo carga con "source" dentro de su propio proceso, el "set -eu" de abajo se
# le queda puesto y el arranque inicial se cae más adelante con "MYSQL_ONETIME_PASSWORD: unbound
# variable" (el contenedor se reinicia y el primer despliegue falla).

set -eu

if [ -z "${MYSQL_MIGRATOR_USER:-}" ] || [ -z "${MYSQL_MIGRATOR_PASSWORD:-}" ]; then
  echo "Las credenciales de Relational Migrator son obligatorias" >&2
  exit 1
fi

mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
CREATE USER IF NOT EXISTS '${MYSQL_MIGRATOR_USER}'@'%' IDENTIFIED BY '${MYSQL_MIGRATOR_PASSWORD}';
ALTER USER '${MYSQL_MIGRATOR_USER}'@'%' IDENTIFIED BY '${MYSQL_MIGRATOR_PASSWORD}';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT
ON *.* TO '${MYSQL_MIGRATOR_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL
