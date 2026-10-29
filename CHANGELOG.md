# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

## [0.3.0] - 2026-10-29

Persistencia híbrida: los módulos de seguimiento y relación con el cliente pasan de MySQL a
MongoDB; MySQL conserva identidad y seguridad, clientes, proyectos e inventario, ventas, cartera
y pagos, gerencia financiera y las solicitudes de cambio de datos.

### Agregado
- **Colecciones de MongoDB** con ids numéricos de `contadores` y control de concurrencia por
  versión (dos usuarios guardando lo mismo → 409):
  - PQRS (`solicitudes_pqrs`, con mensajes, adjuntos, historial y encuesta embebidos).
  - Soporte y notificaciones (`tickets_soporte`, `notificaciones` con sus envíos).
  - Auditoría (`auditoria_eventos`, `alertas_anomalia`, `exportaciones_auditoria`,
    `reportes_generados`, `intentos_acceso`). La bitácora es inmutable.
  - CRM (`prospectos`, `oportunidades` con lotes de interés, historial, actividades y
    presentaciones; `citas_comerciales`) y `simulaciones` con su cronograma.
  - Seguimiento (`tareas` con su historial, `gestiones_cobro`, `estados_cuenta`).
  - Servicio al cliente (`citas_servicio`, `comunicaciones_cliente`, `categorias_conocimiento`,
    `articulos_conocimiento`).
- **Migradores al arrancar** (`MigradorTablaMongo`): copian a MongoDB lo que sigue solo en MySQL,
  son idempotentes, suben las secuencias por encima de los ids de MySQL y se pueden apagar por
  módulo (`APP_MIGRACION_<MODULO>_ENABLED`).
- **Sincronización MongoDB → MySQL por Change Stream** hacia tablas espejo `lectura_*`, que leen
  los dashboards. Activa por defecto (`APP_SYNC_ENABLED`).
- **Cola `crm_pendientes`**: la reserva, la firma y la anulación del contrato (MySQL) registran en
  su misma transacción el cambio que le toca al CRM y a la simulación en MongoDB; se aplica al
  confirmarse y se reintenta cada 30 s si falla (`APP_CRM_PENDIENTES_INTERVALO`).
- **Alertas de anomalía automáticas**: cuenta bloqueada, inicio de sesión desde IP nueva, 3 o más
  exportaciones de auditoría en 24 h y superadministración entre 22:00 y 06:00. Se notifican a
  quienes tienen `AUDITORIA_ALERTAS_GESTIONAR`.
- **Retención de 90 días** (índices TTL) para intentos de acceso y solicitudes de reportes.
- **Índices de MongoDB al arrancar** para todas las colecciones (`APP_MONGO_INDICES_ENABLED`).
- **Dashboards al día**: al proyectar un cambio se borran de Redis los dashboards de esa empresa
  (cada 2 s, `APP_SYNC_INVALIDACION_DASHBOARDS`).
- **Reconstrucción de tablas espejo** desde MongoDB si se pierde el historial del Change Stream
  (error 286), si no hay token o con `APP_SYNC_RECONSTRUIR_AL_ARRANCAR=true`.

### Cambiado
- Las notificaciones y los eventos de auditoría se escriben después del commit de la operación
  que los origina: si hay rollback no se crean, y si MongoDB falla la operación no se cae (el
  evento queda en el log).
- Los dashboards de servicio, comercial y gerencial leen PQRS, encuestas, tickets, citas, tareas y
  oportunidades de las tablas espejo; el de cartera cuenta las promesas de pago en MongoDB.
- El portal del cliente cuenta sus PQRS y busca su próxima cita en MongoDB.
- La reserva ya no bloquea la simulación en MySQL: la valida y la marca aceptada por la cola.
- Las solicitudes de cambio de datos se quedan en MySQL: aprobarlas cambia la persona y el usuario
  en la misma transacción.

### Corregido
- El listado de simulaciones fallaba para usuarios con alcance de "registros propios" después de
  mover las oportunidades a MongoDB.
- Los índices únicos y TTL de una colección no se creaban si su migrador estaba apagado.
- Los dashboards podían mostrar datos de hasta 5 minutos atrás tras un cambio en MongoDB.

### Notas para actualizar
- Flyway aplica V24–V29: sueltan las llaves foráneas hacia tablas que ahora viven en MongoDB,
  crean `crm_pendientes` y siembran las tablas espejo con el histórico.
- MongoDB debe correr como replica set (`rs0`); el primer arranque copia los datos de MySQL a
  MongoDB. Las tablas originales de MySQL se conservan sin escrituras como respaldo hasta una
  versión posterior.
- Variables nuevas, todas con valor por defecto: `APP_MIGRACION_*_ENABLED`, `APP_MONGO_INDICES_ENABLED`,
  `APP_CRM_PENDIENTES_INTERVALO`, `APP_SYNC_INVALIDACION_DASHBOARDS`,
  `APP_SYNC_RECONSTRUIR_AL_ARRANCAR`.
- Revisar después del arranque: `SELECT * FROM crm_pendientes WHERE procesado_en IS NULL` debe
  quedar vacío.

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