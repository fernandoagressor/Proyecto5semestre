# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Agregado
- **Rediseño, fase 0: sistema visual** del tablero "NEXORA CORE — Vistas propuestas". Manrope
  para títulos, Inter para el texto y JetBrains Mono para bitácoras; acento índigo `#4f46e5` sobre
  fondo `#f4f6fb`. Piezas reutilizables en `frontend/src/app/shared/ui` (encabezado de página,
  indicador, insignia con tono por estado, pestañas, filtros en píldoras, panel lateral y diálogo,
  estados de carga/error/vacío, formatos de pesos y fechas de Colombia) y clases globales `nx-`
  en `frontend/src/styles/_nx.scss` (tarjetas, botones, campos, tablas, avisos).
- **Rediseño, fase 1: Gerencia y Auditoría** reemplazan la vista provisional.
  - Gerencia (`/gerencia`), con pestañas según permisos: **Metas** (cumplimiento por asesor con
    barra y marca del 100 %, proyección de cierre del mes, asignar, editar, aprobar y cerrar
    metas), **Comisiones** (totales por estado, aprobación en lote, causar y pagar con fecha,
    anular, proyectar desde un contrato y reglas de comisión), **Presupuesto y costos**
    (presupuesto contra ejecutado por categoría, costos por aprobar, presupuestos, categorías y
    registro de costos) y **Rentabilidad** (ventas, recaudo, costos, utilidad y margen por rango).
  - Auditoría (`/auditoria`): alertas de anomalías (asignar, cerrar como atendida o descartar),
    bitácora filtrable por módulo, usuario, acción y rango con el antes y el después de cada
    cambio, exportación a CSV con motivo (queda registrada en la API) y tareas vencidas.
- **Rediseño, fase 2a: Comercial y Simulador.**
  - Comercial (`/crm`): indicadores (valor del embudo, abiertas, ganadas y tasa de cierre del
    mes, días al cierre), embudo en **tablero** por etapa (con el total real de cada etapa) o en
    **tabla** paginada, y un **panel de la oportunidad** con avance por etapas, datos del cliente,
    probabilidad, línea de tiempo (actividades, cambios de etapa y citas), registrar actividad,
    agendar cita, editar valor y próxima acción, cerrar como perdida, reabrir y lotes de interés.
    Prospectos con filtros, editar, calificar, reasignar y crear oportunidad; agenda con
    confirmar, realizada, reprogramar, no asistió y cancelar.
  - Simulador (`/simulador`, nuevo en el menú): condiciones con deslizadores (cuota inicial,
    plazo, interés, periodicidad, día de pago), vista previa inmediata con cuota fija, gráfica de
    capital e intereses por año, cronograma, y al guardar el plan oficial calculado por la API;
    historial de simulaciones de la oportunidad con enviar y cancelar.
- **Rediseño, fase 2b: Reservas y contratos** (`/ventas`) con el estilo del diseño: indicadores,
  pestañas con conteo, reservas con días para el vencimiento y acciones directas (confirmar pago,
  crear contrato), contratos en tabla, expediente del contrato con la ruta Borrador → Negociación
  → En firma → Vigente → Saldado, firmantes, participantes y documentos; todos los formularios en
  paneles laterales o diálogos.
- **Rediseño, fase 2c: Cartera** (`/cartera`) con el sistema visual y secciones nuevas:
  **Refinanciaciones** (proponer sobre el saldo, aprobar, rechazar, aplicar al plan y ver las
  cuotas propuestas), **Conciliación bancaria** (crear por cuenta y rango, cargar movimientos del
  extracto, cruzarlos con pagos confirmados sugiriendo los de igual valor, ignorar y cerrar),
  **reversiones pendientes** con aprobar o rechazar (solo otro usuario) y **Cierre de mes**.
- `GET /api/v1/pagos/reversiones?estado=`: solicitudes de reversión de la empresa (antes no había
  forma de encontrarlas para aprobarlas).
- La Prueba de humo E2E carga datos de ejemplo (`carga/semilla.js`, volumen pequeño) antes de las
  capturas, para que muestren tablas y tableros con contenido.
- **Rediseño, fase 2d: Servicio** (`/servicio`): indicadores, bandeja de PQRS con lista y detalle
  lado a lado (conversación, adjuntos, historial, cambio de estado), **asignar responsable**,
  **usar un artículo** de la base de conocimiento en la respuesta y **escalar a ticket**; citas en
  tarjetas; pestañas nuevas de **Tickets internos** (crear, asignar, cambiar estado, conversación
  con notas internas) y **Base de conocimiento** (buscar, crear y editar artículos, publicar,
  archivar, categorías).
- **Rediseño, fase 3a: Dashboard e inicio de sesión.** El dashboard sigue la vista
  "Dashboard-Gerencia": periodo en botones, indicadores, análisis principal con lo que requiere
  atención y la salud del negocio, cartera vencida por edades, embudo comercial con conversión
  entre etapas e inventario de lotes por proyecto (vendidos, reservados, disponibles). El inicio de
  sesión pasa a dos paneles: mensajes rotativos sobre fondo índigo y formulario sin Material.
- `GET /api/v1/empresas/actual/usuarios`: directorio del equipo de la empresa del token (nombre,
  usuario, estado y roles; sin correo ni documento) para mostrar nombres en lugar de ids.
- Mensajes de resultado (`nx-toaster`) en todas las pantallas.
- Capturas de pantalla de las rutas principales en escritorio y celular en cada PR (artefacto
  `capturas` de la Prueba de humo E2E, `scripts/capturas.mjs`), con los errores de la consola.
- Prueba de humo de punta a punta en el CI (`Prueba de humo E2E`): levanta el stack completo con
  `docker compose` y recorre frontend, sesión, dashboards, MySQL, MongoDB, sincronización y
  auditoría con `scripts/prueba-humo.sh`, que también sirve contra staging.
- **Configuración de producción** (`compose.prod.yaml`): ninguna contraseña por defecto (compose
  se niega a arrancar si falta una), bases y API sin puertos públicos, Caddy como única entrada
  con HTTPS automático y HSTS, límites de memoria por servicio y rotación de logs. Perfil `prod`
  con pools ajustados (MySQL 30, MongoDB 100), hilos de Tomcat y compresión.
- Guía de despliegue en AWS (`docs/DESPLIEGUE_AWS.md`) y plantilla de variables del servidor
  (`deploy/produccion.env.example`).
- **Monitoreo** (`compose.monitoring.yaml`): Prometheus (15 días) y Grafana con el tablero
  "NEXORA – Producción y pruebas de carga", más exportadores de la máquina, los contenedores,
  MySQL, MongoDB y Redis. Solo en `127.0.0.1` del servidor, por túnel SSH.
- Métricas de la API en `/actuator/prometheus` (solo red interna), con histogramas de latencia
  por endpoint, y métricas propias: `nexora_sync_retraso` (de la escritura en MongoDB a la tabla
  espejo) y `nexora_crm_pendientes` / `nexora_crm_pendientes_agotados`.
- **CI/CD**: el workflow "Publicar imágenes" publica la API y el frontend en GHCR con cada merge a
  `develop` (`develop`, `sha-<commit>`) y con cada tag (`X.Y.Z`, `latest`); "Desplegar" las
  instala por SSH en staging o producción con `deploy/desplegar.sh`, que vuelve a la versión
  anterior si la nueva no queda sana. Staging se despliega solo con `STAGING_AUTODEPLOY=true` y
  corre la prueba de humo; producción exige aprobación.
- El CI también levanta `compose.prod.yaml` con HTTPS y monitoreo y le pasa la prueba de humo;
  comprueba que solo Caddy publique puertos, que `/actuator` esté cerrado, que Prometheus tenga
  todos sus objetivos arriba y que Grafana traiga el tablero.
- **Respaldos**: `deploy/respaldar.sh` respalda cada día MySQL (transacción consistente),
  MongoDB y los documentos subidos, con manifiesto y sha256, y los sube a S3 (el servidor no puede
  borrarlos). `deploy/probar-restauracion.sh` comprueba cada semana, en bases temporales, que el
  último respaldo se pueda restaurar. `deploy/restaurar.sh` restaura un respaldo y reconstruye las
  tablas espejo. `deploy/instalar-respaldos.sh` deja programado todo; Grafana muestra cuánto hace
  del último respaldo y de la última prueba.
- `deploy/desplegar.sh` saca un respaldo antes de cada despliegue.
- **Pruebas de carga con k6** (`carga/`, guía en `docs/PRUEBAS_DE_CARGA.md`): `semilla.js` genera
  datos de volumen por la API (10 proyectos, 2.000 lotes, 100 usuarios de prueba por rol, clientes,
  prospectos, oportunidades, reservas, tareas y PQRS); `carga.js` simula asesores, servicio al
  cliente, cartera y gerencia con escenarios humo, carga (100 usuarios), estrés (hasta 300), pico y
  resistencia (1 h), con los criterios p95 < 800 ms en lecturas, < 1,5 s en escrituras y errores
  < 1 %. El workflow "Prueba de carga" los corre contra staging y los valida en cada PR.
- El CI respalda el stack de producción, lo daña a propósito, lo restaura y comprueba MySQL,
  MongoDB, documentos, tablas espejo e inicio de sesión.

### Cambiado
- Menú lateral claro con grupos Inventario, Ventas y Gestión, logotipo de la empresa y usuario con
  cierre de sesión; barra superior con la ubicación (grupo y pantalla). Aparecen en el menú
  Comercial y Nuevo cliente y lote, que solo se abrían por enlace. Cada enlace exige los mismos
  permisos y módulo que su ruta (prueba en `navigation.config.spec.ts`).
- nginx solo expone `/actuator/health`, toma la IP real del cliente detrás de Caddy, acepta
  subidas de hasta 16 MB (igual que la API) y agrega cabeceras de seguridad y gzip.
- `.env` sale del repositorio: las contraseñas que estuvieron publicadas no se usan en ningún
  servidor.

### Corregido
- El primer arranque de MySQL en una base nueva se caía y el contenedor se reiniciaba
  (`MYSQL_ONETIME_PASSWORD: unbound variable`): `01-create-migrator-user.sh` no era ejecutable, la
  imagen lo cargaba con `source` y su `set -eu` afectaba al resto del arranque. Hizo fallar el
  primer despliegue en AWS. El CI ahora comprueba que ningún contenedor se reinicie.
- `respaldar.sh` fallaba sobre una base recién creada, sin las tablas de Flyway, y con eso
  bloqueaba el primer despliegue.
- Dos inicios de sesión simultáneos del mismo usuario (dos dispositivos a la vez) hacían que uno
  respondiera 409: registrar el acceso guardaba el usuario completo con su versión. Lo encontró la
  prueba de carga.
- Crear o reasignar tareas, asignar metas y asignar alertas de auditoría respondían 500: la
  validación del usuario filtraba por una columna `activo` que no existe en `usuarios`.
- MongoDB 8.0 en `compose.yaml` arranca en kernels Linux 6.19+ (SERVER-121912).

## [0.3.1] - 2026-10-29

> Publicada con el tag `v0.3.1`: el tag `v0.3.0` quedó retirado porque el repositorio usa releases
> inmutables y un primer intento se publicó sobre el commit equivocado. No hay cambios respecto de
> lo que se preparó como 0.3.0; el `pom.xml` del commit etiquetado dice `0.3.0`.

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