# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Cambiado
- **PQRS en MongoDB (rama 5).** Las solicitudes viven en la colección `solicitudes_pqrs`, con
  mensajes, adjuntos, historial y encuesta embebidos. Ids numéricos de `contadores` y control de
  concurrencia por versión (dos agentes guardando la misma solicitud → 409).
- Al arrancar, `MigradorSolicitudesPqrs` copia las PQRS de MySQL que aún no estén en MongoDB
  (idempotente; `APP_MIGRACION_PQRS_ENABLED=false` lo apaga) y sube las secuencias.
- El dashboard de servicio y el resumen gerencial leen PQRS y encuestas de las tablas espejo
  `lectura_*`; el resumen del portal cuenta las PQRS en MongoDB.
- La sincronización por Change Stream queda activa por defecto (`APP_SYNC_ENABLED=true`).
- V24: `citas_servicio.solicitud_id` ya no tiene llave foránea y las tablas espejo se siembran
  con el histórico de MySQL. Las tablas PQRS de MySQL quedan sin escrituras hasta la rama 8.

## [0.2.0] - 2026-10-20

### Agregado
- MongoDB como replica set `rs0`, con transacciones y Change Streams habilitados.
- Gestores de transacción JPA (primario, MySQL) y MongoDB declarados explícitamente.
- `SecuenciaService`: identificadores numéricos atómicos para documentos de MongoDB.
- Caché en Redis de los cuatro dashboards (TTL 5 min, por empresa y usuario).
- Caché en Redis de `token_version`, invalidada al cambiar o reiniciar credenciales.
- Límite de 20 intentos fallidos de login por IP en 15 minutos (respuesta 429).
- Integración continua con GitHub Actions: pruebas del backend y build del frontend en cada PR.

### Corregido
- La IP del cliente ya no se puede falsificar con `X-Forwarded-For` (nginx usa `$remote_addr`).
- Prueba de integración: `MongoDBContainer` de Testcontainers 2 y triggers de MySQL habilitados.
- `ArquitecturaCapasTest` detectaba como paquete legado los imports de `org.springframework.web`.
- MongoDB 8.0 en kernels Linux 6.19+ dentro de las pruebas (SERVER-121912).

## [0.1.0] - 2026-10-06

### Agregado
- Versión inicial de NEXORA CORE: backend por capas, 22 migraciones Flyway,
  módulos de inventario, CRM, ventas, cartera, servicio, gerencia y frontend Angular.