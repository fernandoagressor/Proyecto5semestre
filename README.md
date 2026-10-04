# inmobiliaria-api

Backend multiempresa de la Plataforma de Gestión Inmobiliaria.

## Base técnica

- Java 21.
- Spring Boot 3.5.16.
- MongoDB 8.0 como destino documental principal de la migración.
- MySQL 8.4 LTS como origen actual y almacenamiento transaccional durante la transición.
- Redis 8.2 para caché y coordinación efímera.
- Angular 20.3 y Angular Material 20.2 en `frontend/`.
- Flyway.
- Spring Security con JWT HS256 y refresh tokens opacos.
- Moneda funcional: COP.
- Zona horaria: `America/Bogota`.

El repositorio se llama `inmobiliaria-api`. El nombre TerraCore no se utiliza
como nombre técnico del backend.

## Arquitectura del backend por capas

El paquete raíz `com.inmobiliaria.inmobiliaria_api` utiliza siete capas
explícitas. Cada capa conserva subpaquetes por módulo de negocio para evitar
mezclar responsabilidades de inventario, ventas, cartera, CRM y servicio:

```text
src/main/java/com/inmobiliaria/inmobiliaria_api/
├── config/       # propiedades y configuración general
├── controller/   # endpoints REST por módulo
├── dto/          # contratos de entrada y salida de la API
├── entity/       # entidades JPA, estados y modelos persistentes
├── repository/   # acceso a datos con Spring Data JPA
├── security/     # JWT, filtros y contexto autenticado
├── service/      # casos de uso, transacciones y reglas de aplicación
└── InmobiliariaApiApplication.java
```

Ejemplo: el módulo de inventario se distribuye entre
`controller/inventario`, `dto/inventario`, `entity/inventario`,
`repository/inventario` y `service/inventario`. Los controladores no acceden
directamente a los repositorios: siempre coordinan la operación mediante un
servicio.

La guía completa está en
[`docs/ARQUITECTURA_BACKEND_POR_CAPAS.md`](docs/ARQUITECTURA_BACKEND_POR_CAPAS.md).
Los controles ejecutados y los pasos para validarla en IntelliJ están en
[`docs/VALIDACION_REORGANIZACION.md`](docs/VALIDACION_REORGANIZACION.md).

## Estado actual

- Veintiuna migraciones Flyway del modelo normalizado.
- 97 tablas y datos iniciales de roles/permisos.
- Inicio de sesión por correo o nombre de usuario.
- Access token JWT y refresh token rotativo.
- Cierre y revocación de sesión.
- Cambio obligatorio y voluntario de contraseña con validación segura.
- Invalidación inmediata de JWT y refresh tokens tras cambiar o reiniciar credenciales.
- Registro de accesos exitosos, fallidos y bloqueados.
- Bloqueo temporal tras cinco intentos fallidos.
- Autoridades cargadas desde roles y permisos.
- Consulta del usuario autenticado.
- Consulta de la empresa del usuario autenticado.
- Creación opcional y segura del primer administrador.
- Health check para despliegue.
- Módulo de proyectos, etapas, manzanas y lotes.
- Filtros y paginación del inventario.
- Alcances por empresa, proyecto y registros propios.
- Historial de estados y precios de cada lote.
- Bloqueo pesimista y versión optimista para cambios sensibles.
- Resumen de disponibilidad y valor comercial disponible.
- CRM comercial con prospectos, oportunidades y embudo.
- Lotes de interés, actividades y agenda comercial sin cruces.
- Simulaciones financieras con cronograma de cuotas.
- Control optimista de concurrencia en prospectos, oportunidades, citas y
  simulaciones.
- Reservas transaccionales con vencimiento y liberación automática del lote.
- Confirmación o rechazo de pagos de reserva con separación de permisos.
- Contratos versionados con participantes, documentos y firmantes.
- Activación de la venta únicamente después de completar firmas válidas.
- Anulación trazable con reapertura controlada del lote y la oportunidad.
- Planes de pago versionados y cronogramas financieros por contrato.
- Cuotas con saldos, días de mora e interés moratorio automático.
- Recaudos con revisión, confirmación, aplicación exacta y rechazo.
- Reversiones con doble control y restauración transaccional de saldos.
- Conciliación bancaria de movimientos contra pagos confirmados.
- Gestiones de cobro, promesas de pago y refinanciaciones aprobables.
- Estados de cuenta, paz y salvos y cierres mensuales reproducibles.
- Centro de cartera Angular con indicadores, contratos, cronogramas, recaudos,
  vencimientos, gestiones de cobro y exportación CSV.
- Generación automática del plan al completar las firmas y sincronización
  idempotente de contratos vigentes creados antes del módulo operativo.
- Portal privado con resumen, documentos visibles y solicitudes de cambio de
  datos personales.
- PQRS con conversación, notas internas, adjuntos, asignación, SLA, historial y
  encuesta de satisfacción.
- Citas de servicio sin cruces, comunicaciones y base de conocimiento.
- Centro de servicio Angular con resumen operativo, bandeja de PQRS,
  conversación, adjuntos, historial y agenda; portal privado diferenciado para
  el cliente autenticado.
- Notificaciones internas personales y tickets técnicos trazables.
- Dashboards comercial, de cartera, de servicio y gerencial calculados sobre
  datos transaccionales, con histórico completo predeterminado y filtros por
  mes actual, últimos tres meses, año actual y proyecto.
- Metas comerciales con aprobación, cierre y medición de cumplimiento.
- Reglas de comisión versionadas y flujo proyectada → causada → aprobada →
  pagada, con separación entre beneficiario y aprobador.
- Tareas con prioridad, reasignación, vencimiento automático e historial.
- Presupuestos, costos reales y rentabilidad por proyecto y periodo.
- Reportes solicitados mediante cola trazable y archivos privados.
- Consulta de auditoría inmutable, exportaciones y gestión de alertas de
  anomalía.
- Frontend Angular base con autenticación, renovación de sesión y cierre seguro.
- Navegación responsive filtrada por roles y permisos.
- Dashboards gerencial, comercial, cartera, servicio y portal cliente.
- Consulta paginada de proyectos y centro de notificaciones.
- Inventario operativo con resumen, filtros, etapas, manzanas, lotes,
  disponibilidad e historiales de precio y estado.
- CRM operativo con bandeja de prospectos, pipeline, agenda comercial y
  simulaciones financieras.
- Centro de ventas operativo con clientes, reservas, validación documental de
  pagos, contratos, participantes, versiones y firmas.
- Carga y descarga privada de PDF, PNG y JPG con huella SHA-256, límite de
  15 MB y almacenamiento aislado por empresa y proyecto.
- Formularios y acciones visibles según permisos, conservando el backend como
  autoridad final para empresa, proyecto y registro.
- Consola global protegida para `SUPER_ADMIN` con resumen de plataforma,
  búsqueda y gobierno del estado de cada empresa.
- Creación transaccional de empresa y administrador inicial, con límites de
  proyectos y usuarios controlados por el backend.
- Administración de usuarios internos, roles personalizados, permisos,
  alcances y reinicio seguro de credenciales.
- Matriz de dashboards completa para roles protegidos, incluido
  `ADMINISTRADOR` y `AUDITOR`.
- Cambio rotativo del espacio de trabajo de `SUPER_ADMIN`, persistido en la
  sesión renovable y auditado por empresa.
- Alta y edición de proyectos desde la consola, con acceso directo al
  inventario de etapas, manzanas y lotes.
- Modo claro/oscuro e identidad visual NEXORA CORE.

## Ejecución local con Docker

1. Copiar `.env.example` a `.env`.
2. Reemplazar todas las credenciales de ejemplo.
3. Ejecutar:

```bash
docker compose up --build -d
```

Flyway creará el esquema relacional de MySQL al iniciar la API. MongoDB crea
usuarios separados para la aplicación y Relational Migrator; las colecciones de
negocio se incorporarán módulo por módulo desde el modelo aprobado.

Comprobar toda la infraestructura:

```bash
./scripts/provision-migrator-users.sh
./scripts/verify-datastores.sh
```

Relational Migrator se ejecuta mediante un perfil opcional. La imagen local
incorpora MySQL Connector/J durante su construcción:

```bash
docker compose --profile migration up --build -d relational-migrator
```

Su interfaz queda disponible en `http://localhost:8278`.

La aplicación web queda disponible en:

```text
http://localhost:4200
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

Readiness de MySQL, MongoDB y Redis:

```text
GET http://localhost:8080/actuator/health/readiness
```

## Migración MySQL → MongoDB

El diseño se realizará en Moon Modeler y la transferencia se ejecutará con
MongoDB Relational Migrator. Los archivos `.dmm`, `.relmig`, validadores,
índices e informes se conservarán en `migration/`.

La estrategia, responsabilidades y criterios de aprobación están descritos en
[`docs/PERSISTENCIA_POLIGLOTA_Y_MIGRACION.md`](docs/PERSISTENCIA_POLIGLOTA_Y_MIGRACION.md).

## Autenticación

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/cambiar-password
POST /api/v1/auth/contexto
GET  /api/v1/auth/me
GET  /api/v1/empresas/actual

GET   /api/v1/admin/resumen
GET   /api/v1/admin/empresas
POST  /api/v1/admin/empresas
PUT   /api/v1/admin/empresas/{empresaId}
PATCH /api/v1/admin/empresas/{empresaId}/estado

GET   /api/v1/admin/empresas/{empresaId}/usuarios
POST  /api/v1/admin/empresas/{empresaId}/usuarios
PUT   /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}
PATCH /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}/estado
POST  /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}/credenciales

GET   /api/v1/admin/empresas/{empresaId}/roles
POST  /api/v1/admin/empresas/{empresaId}/roles
PUT   /api/v1/admin/empresas/{empresaId}/roles/{rolId}
GET   /api/v1/admin/permisos

GET   /api/v1/admin/empresas/{empresaId}/proyectos
POST  /api/v1/admin/empresas/{empresaId}/proyectos
PUT   /api/v1/admin/empresas/{empresaId}/proyectos/{proyectoId}

GET   /api/v1/proyectos
POST  /api/v1/proyectos
GET   /api/v1/proyectos/{proyectoId}
PUT   /api/v1/proyectos/{proyectoId}
PATCH /api/v1/proyectos/{proyectoId}/estado

GET   /api/v1/proyectos/{proyectoId}/etapas
POST  /api/v1/proyectos/{proyectoId}/etapas
PUT   /api/v1/proyectos/{proyectoId}/etapas/{etapaId}

GET   /api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas
POST  /api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas
PUT   /api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas/{manzanaId}

GET   /api/v1/proyectos/{proyectoId}/lotes
POST  /api/v1/proyectos/{proyectoId}/lotes
GET   /api/v1/proyectos/{proyectoId}/lotes/{loteId}
PUT   /api/v1/proyectos/{proyectoId}/lotes/{loteId}
PATCH /api/v1/proyectos/{proyectoId}/lotes/{loteId}/estado
PATCH /api/v1/proyectos/{proyectoId}/lotes/{loteId}/precio
GET   /api/v1/proyectos/{proyectoId}/inventario/resumen

GET   /api/v1/prospectos
POST  /api/v1/prospectos
PUT   /api/v1/prospectos/{prospectoId}
PATCH /api/v1/prospectos/{prospectoId}/calificacion
PATCH /api/v1/prospectos/{prospectoId}/asesor

GET   /api/v1/oportunidades
POST  /api/v1/oportunidades
PUT   /api/v1/oportunidades/{oportunidadId}
PATCH /api/v1/oportunidades/{oportunidadId}/etapa
PATCH /api/v1/oportunidades/{oportunidadId}/cerrar-perdida
PATCH /api/v1/oportunidades/{oportunidadId}/reabrir
GET   /api/v1/oportunidades/{oportunidadId}/lotes
POST  /api/v1/oportunidades/{oportunidadId}/lotes
GET   /api/v1/oportunidades/{oportunidadId}/actividades
POST  /api/v1/oportunidades/{oportunidadId}/actividades
GET   /api/v1/oportunidades/{oportunidadId}/citas
POST  /api/v1/oportunidades/{oportunidadId}/citas

GET   /api/v1/agenda/comercial

GET   /api/v1/simulaciones
POST  /api/v1/simulaciones
GET   /api/v1/simulaciones/{simulacionId}
PATCH /api/v1/simulaciones/{simulacionId}/enviar
PATCH /api/v1/simulaciones/{simulacionId}/cancelar

GET   /api/v1/clientes
POST  /api/v1/clientes
GET   /api/v1/clientes/{clienteId}
PUT   /api/v1/clientes/{clienteId}
PATCH /api/v1/clientes/{clienteId}/estado

GET   /api/v1/reservas
POST  /api/v1/reservas
GET   /api/v1/reservas/{reservaId}
PATCH /api/v1/reservas/{reservaId}/vencimiento
PATCH /api/v1/reservas/{reservaId}/pago/confirmar
PATCH /api/v1/reservas/{reservaId}/pago/rechazar
PATCH /api/v1/reservas/{reservaId}/cancelar
POST  /api/v1/reservas/{reservaId}/contrato

GET   /api/v1/contratos
GET   /api/v1/contratos/{contratoId}
PUT   /api/v1/contratos/{contratoId}
POST  /api/v1/contratos/{contratoId}/clientes
POST  /api/v1/contratos/{contratoId}/firmantes
POST  /api/v1/contratos/{contratoId}/versiones
PATCH /api/v1/contratos/{contratoId}/negociacion
PATCH /api/v1/contratos/{contratoId}/enviar-firma
POST  /api/v1/contratos/{contratoId}/firmantes/{firmanteId}/firmas
PATCH /api/v1/contratos/{contratoId}/anular

POST  /api/v1/ventas/documentos
GET   /api/v1/ventas/documentos/{archivoId}

GET   /api/v1/cartera/planes-pago
POST  /api/v1/cartera/planes-pago
PATCH /api/v1/cartera/planes-pago/{planId}/activar
POST  /api/v1/cartera/planes-pago/contratos/{contratoId}/sincronizar

GET   /api/v1/cartera/resumen
GET   /api/v1/cartera/contratos
GET   /api/v1/cartera/metodos-pago
GET   /api/v1/cartera/configuracion
PUT   /api/v1/cartera/configuracion

GET   /api/v1/pagos
POST  /api/v1/pagos
PATCH /api/v1/pagos/{pagoId}/revision
PATCH /api/v1/pagos/{pagoId}/confirmar
PATCH /api/v1/pagos/{pagoId}/rechazar
POST  /api/v1/pagos/{pagoId}/reversiones
PATCH /api/v1/pagos/{pagoId}/reversiones/{reversionId}/aprobar
PATCH /api/v1/pagos/{pagoId}/reversiones/{reversionId}/rechazar

GET   /api/v1/conciliaciones-bancarias
POST  /api/v1/conciliaciones-bancarias
POST  /api/v1/conciliaciones-bancarias/{id}/movimientos
PATCH /api/v1/conciliaciones-bancarias/{id}/movimientos/{movimientoId}/conciliar
PATCH /api/v1/conciliaciones-bancarias/{id}/movimientos/{movimientoId}/ignorar
PATCH /api/v1/conciliaciones-bancarias/{id}/cerrar

POST  /api/v1/cartera/mora/actualizar
GET   /api/v1/cartera/mora
GET   /api/v1/cartera/gestiones-cobro
POST  /api/v1/cartera/gestiones-cobro
GET   /api/v1/cartera/refinanciaciones
POST  /api/v1/cartera/refinanciaciones
PATCH /api/v1/cartera/refinanciaciones/{id}/aprobar
PATCH /api/v1/cartera/refinanciaciones/{id}/rechazar
PATCH /api/v1/cartera/refinanciaciones/{id}/aplicar
POST  /api/v1/cartera/estados-cuenta
POST  /api/v1/cartera/paz-salvos
POST  /api/v1/cartera/cierres

GET   /api/v1/portal/resumen
GET   /api/v1/portal/documentos
GET   /api/v1/portal/documentos/{archivoId}/descargar
GET   /api/v1/portal/cambios-datos
POST  /api/v1/portal/cambios-datos

GET   /api/v1/solicitudes
POST  /api/v1/solicitudes
GET   /api/v1/solicitudes/{id}/mensajes
POST  /api/v1/solicitudes/{id}/mensajes
GET   /api/v1/solicitudes/{id}/adjuntos
POST  /api/v1/solicitudes/{id}/adjuntos/archivo
GET   /api/v1/solicitudes/{id}/adjuntos/{archivoId}/descargar
GET   /api/v1/solicitudes/{id}/historial
GET   /api/v1/citas-servicio
POST  /api/v1/citas-servicio
PATCH /api/v1/citas-servicio/{id}/reprogramar
PATCH /api/v1/citas-servicio/{id}/estado

GET   /api/v1/comunicaciones-clientes
POST  /api/v1/comunicaciones-clientes
GET   /api/v1/base-conocimiento/articulos

GET   /api/v1/notificaciones
GET   /api/v1/soporte/tickets
POST  /api/v1/soporte/tickets

GET   /api/v1/dashboards/gerencial
GET   /api/v1/dashboards/comercial
GET   /api/v1/dashboards/cartera
GET   /api/v1/dashboards/servicio

GET   /api/v1/comisiones/reglas
POST  /api/v1/comisiones/reglas
GET   /api/v1/comisiones
POST  /api/v1/comisiones
PATCH /api/v1/comisiones/{id}/causar
PATCH /api/v1/comisiones/{id}/aprobar
PATCH /api/v1/comisiones/{id}/pagar

GET   /api/v1/metas-comerciales
POST  /api/v1/metas-comerciales
PATCH /api/v1/metas-comerciales/{id}/aprobar
GET   /api/v1/metas-comerciales/{id}/cumplimiento

GET   /api/v1/tareas
POST  /api/v1/tareas
PATCH /api/v1/tareas/{id}/estado
PATCH /api/v1/tareas/{id}/asignacion

GET   /api/v1/gerencia/presupuestos
POST  /api/v1/gerencia/presupuestos
GET   /api/v1/gerencia/costos
POST  /api/v1/gerencia/costos
GET   /api/v1/gerencia/rentabilidad

GET   /api/v1/reportes
POST  /api/v1/reportes

GET   /api/v1/auditoria/eventos
GET   /api/v1/auditoria/alertas
POST  /api/v1/auditoria/exportaciones
```

Ejemplo de acceso:

```json
{
  "identificador": "admin@example.com",
  "password": "la-clave-configurada"
}
```

El bootstrap del administrador está deshabilitado por defecto. Al usarlo en la
primera instalación, debe deshabilitarse después del primer arranque.

## Variables obligatorias

```text
DB_URL
DB_USER
DB_PASSWORD
JWT_SECRET
```

`JWT_SECRET` debe tener como mínimo 32 bytes y debe manejarse con secretos del
proveedor de despliegue. Nunca debe confirmarse un archivo `.env` en Git.

## Compilación

```bash
mvn clean verify
```

Se requiere Maven 3.9 o superior y JDK 21.

El alcance y las reglas de los módulos están descritos en:

- `docs/SEGUNDO_CORTE_INVENTARIO.md`.
- `docs/TERCER_CORTE_CRM_COMERCIAL.md`.
- `docs/CUARTO_CORTE_RESERVAS_CONTRATOS.md`.
- `docs/QUINTO_CORTE_CARTERA_PAGOS.md`.
- `docs/SEXTO_CORTE_PORTAL_SERVICIO_CLIENTE.md`.
- `docs/SEPTIMO_CORTE_GERENCIA_CONTROL.md`.
- `docs/OCTAVO_CORTE_FRONTEND_ANGULAR.md`.
- `docs/NOVENO_CORTE_FRONTEND_INVENTARIO_CRM.md`.
- `docs/DECIMO_CORTE_SUPERADMINISTRACION.md`.
- `docs/DECIMOTERCER_CORTE_CARTERA_OPERATIVA.md`.
- `docs/DECIMOCUARTO_CORTE_PORTAL_SERVICIO_CLIENTE_OPERATIVO.md`.
