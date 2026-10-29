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
- **Tickets de soporte en MongoDB (rama 6).** Colección `tickets_soporte` con mensajes, adjuntos
  e historial embebidos; ids de `contadores` y control por versión (409 en conflicto).
- **Notificaciones en MongoDB (rama 6).** Colección `notificaciones` con los envíos embebidos.
  Se escriben después del commit de la transacción que las origina (si hay rollback no se crean)
  y un fallo al guardarlas no tumba la operación principal. Marcar como leída es atómico.
- Los migradores comparten la base `MigradorTablaMongo`; se agregan `MigradorTicketsSoporte`
  (`APP_MIGRACION_SOPORTE_ENABLED`) y `MigradorNotificaciones` (`APP_MIGRACION_NOTIFICACIONES_ENABLED`).
- El dashboard de servicio cuenta los tickets desde `lectura_tickets_soporte`; V25 la siembra.
- **Auditoría en MongoDB (rama 7).** Colecciones `auditoria_eventos`, `alertas_anomalia`,
  `exportaciones_auditoria`, `reportes_generados` e `intentos_acceso`. La bitácora es inmutable
  (su repositorio solo inserta y consulta) y los eventos se escriben después del commit; si
  MongoDB falla, el evento completo queda en el log como ERROR. Los JSON de evidencia se guardan
  como texto exacto. Asignar/cerrar alertas y completar reportes usan control por versión (409).
  Migradores con `APP_MIGRACION_AUDITORIA_ENABLED`.
- Retención de 90 días: MongoDB borra solos los intentos de acceso y las solicitudes de reportes
  (índices TTL).
- **Alertas de anomalía automáticas.** `DetectorAnomaliasService` crea alertas por cuenta bloqueada,
  inicio de sesión desde una IP nueva, 3 o más exportaciones de auditoría en 24 horas y acciones de
  superadministración entre las 22:00 y las 06:00. No repite una alerta abierta del mismo tipo y
  usuario, y notifica a quienes tienen `AUDITORIA_ALERTAS_GESTIONAR`.
- **CRM en MongoDB (rama 8a).** Colecciones `prospectos`, `oportunidades` (con lotes de interés,
  historial de etapas, actividades y presentaciones embebidos) y `citas_comerciales`. Montos como
  Decimal128; control por versión (409).
- **Cola `crm_pendientes` (V26).** La reserva, la firma y la anulación del contrato escriben en
  MySQL, dentro de su transacción, el cambio que le toca al CRM (cliente de la reserva, venta
  ganada, venta anulada). Se aplica en MongoDB al confirmarse y, si falla, se reintenta cada 30 s
  (`APP_CRM_PENDIENTES_INTERVALO`). La firma manda: gana la oportunidad aunque se hubiera cerrado.
- El flujo cliente-lote borra el prospecto y la oportunidad creados en MongoDB si su transacción
  hace rollback.
- V26 suelta las llaves de reservas, contratos y simulaciones hacia oportunidades y siembra
  `lectura_oportunidades`; el dashboard comercial lee la tabla espejo. Migradores con
  `APP_MIGRACION_CRM_ENABLED`.

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