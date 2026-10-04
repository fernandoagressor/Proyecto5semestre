# Controlador JDBC de MySQL

Relational Migrator no incluye el controlador JDBC de MySQL. La imagen local
definida en `docker/relational-migrator/Dockerfile` obtiene automáticamente la
versión indicada por `MYSQL_CONNECTOR_VERSION` durante la construcción.

Esta carpeta queda reservada para una instalación manual si el entorno no
permite descargar dependencias durante `docker compose build`. No se deben
guardar binarios ni credenciales sin revisar su licencia y la política del
proyecto.
