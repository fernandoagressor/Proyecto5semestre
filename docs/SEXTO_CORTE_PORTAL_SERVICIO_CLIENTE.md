# Sexto corte: Portal y Servicio al Cliente

Fecha: 31 de julio de 2026  
Repositorio: `inmobiliaria-api`  
Base: Java 21, Spring Boot 3.5.16, MySQL 8.4 y Flyway

## Alcance implementado

El corte habilita la atención posterior a la venta y el autoservicio del
cliente autenticado:

1. Resumen privado del portal con contratos, cartera, documentos, solicitudes
   y próxima cita.
2. Consulta de documentos marcados como vigentes y visibles en el portal, sin
   exponer la ubicación física del archivo.
3. Solicitudes de cambio de datos personales con revisión independiente,
   control de dato obsoleto y aplicación transaccional.
4. PQRS con código único, prioridad, fecha límite, conversación, notas
   internas, adjuntos, asignación, historial y encuesta de satisfacción.
5. Agenda de servicio con citas, visitas técnicas o entregas, validación de
   cruces y estados controlados.
6. Registro de comunicaciones individuales y masivas por canal.
7. Base de conocimiento empresarial y global, con publicación exclusiva para
   artículos visibles al cliente.
8. Notificaciones internas personales, conteo de pendientes y lectura.
9. Tickets de soporte técnico con conversación, adjuntos, asignación,
   historial y estados trazables.

## Reglas críticas

- Un usuario cliente solo puede consultar información asociada con su propio
  `cliente_id` y su propia cuenta.
- Los registros con proyecto respetan el alcance por empresa, proyecto o
  registros propios.
- Los registros internos sin proyecto requieren alcance completo de empresa,
  salvo tickets propios.
- Una PQRS debe estar `RESUELTA` antes de quedar `CERRADA`; el cierre exige el
  permiso sensible `SOLICITUDES_CERRAR`.
- Las notas internas de PQRS y soporte nunca se devuelven al cliente.
- Un adjunto debe pertenecer a la misma empresa y estar `ACTIVO`; si se asocia
  con un mensaje, dicho mensaje debe pertenecer a la misma conversación.
- Una cita futura no puede cruzarse con otra cita abierta del mismo
  responsable.
- El cliente puede reprogramar o cancelar sus citas, pero no marcarlas como
  realizadas.
- Quien solicita un cambio de datos no puede aprobarlo ni rechazarlo.
- Antes de aprobar un cambio se comprueba que el dato original no haya cambiado
  desde la solicitud.
- Un correo o documento duplicado provoca reversión completa de la aprobación.
- Un artículo global puede leerse, pero no modificarse desde una empresa.
- Una notificación expirada no aparece ni se cuenta como pendiente.
- El estado `ENVIADA` de una comunicación solo se registra cuando una
  integración confirma la aceptación del envío; no equivale por sí mismo a
  entrega al destinatario.

## Migración `V14`

`V14__integridad_portal_servicio_cliente.sql` agrega, sin modificar `V1–V13`:

- versiones optimistas para PQRS, citas, comunicaciones, artículos, tickets,
  notificaciones y envíos;
- la tabla `solicitudes_cambio_datos`;
- índices para agenda, comunicaciones programadas, entrega de notificaciones y
  artículos del portal;
- permiso de respuesta de soporte para el rol `CLIENTE`, necesario para
  continuar conversaciones de tickets propios.

El esquema queda en 96 tablas.

## Endpoints añadidos

### Portal y datos personales

```text
GET   /api/v1/portal/resumen
GET   /api/v1/portal/documentos
GET   /api/v1/portal/cambios-datos
POST  /api/v1/portal/cambios-datos
PATCH /api/v1/portal/cambios-datos/{id}/aprobar
PATCH /api/v1/portal/cambios-datos/{id}/rechazar
PATCH /api/v1/portal/cambios-datos/{id}/cancelar
```

### PQRS

```text
GET   /api/v1/solicitudes
POST  /api/v1/solicitudes
GET   /api/v1/solicitudes/{id}
GET   /api/v1/solicitudes/{id}/mensajes
POST  /api/v1/solicitudes/{id}/mensajes
PATCH /api/v1/solicitudes/{id}/asignacion
PATCH /api/v1/solicitudes/{id}/estado
PATCH /api/v1/solicitudes/{id}/cerrar
GET   /api/v1/solicitudes/{id}/adjuntos
POST  /api/v1/solicitudes/{id}/adjuntos
GET   /api/v1/solicitudes/{id}/historial
POST  /api/v1/solicitudes/{id}/encuesta
```

### Citas, comunicaciones y conocimiento

```text
GET   /api/v1/citas-servicio
POST  /api/v1/citas-servicio
PATCH /api/v1/citas-servicio/{id}/reprogramar
PATCH /api/v1/citas-servicio/{id}/estado

GET   /api/v1/comunicaciones-clientes
POST  /api/v1/comunicaciones-clientes
POST  /api/v1/comunicaciones-clientes/masivas
PATCH /api/v1/comunicaciones-clientes/{id}/enviada
PATCH /api/v1/comunicaciones-clientes/{id}/fallida

GET   /api/v1/base-conocimiento/categorias
POST  /api/v1/base-conocimiento/categorias
GET   /api/v1/base-conocimiento/articulos
GET   /api/v1/base-conocimiento/articulos/{id}
POST  /api/v1/base-conocimiento/articulos
PUT   /api/v1/base-conocimiento/articulos/{id}
PATCH /api/v1/base-conocimiento/articulos/{id}/publicar
PATCH /api/v1/base-conocimiento/articulos/{id}/archivar
```

### Notificaciones y soporte

```text
GET   /api/v1/notificaciones
GET   /api/v1/notificaciones/resumen
PATCH /api/v1/notificaciones/{id}/leer

GET   /api/v1/soporte/tickets
POST  /api/v1/soporte/tickets
GET   /api/v1/soporte/tickets/{id}
GET   /api/v1/soporte/tickets/{id}/mensajes
POST  /api/v1/soporte/tickets/{id}/mensajes
PATCH /api/v1/soporte/tickets/{id}/asignacion
PATCH /api/v1/soporte/tickets/{id}/estado
GET   /api/v1/soporte/tickets/{id}/adjuntos
POST  /api/v1/soporte/tickets/{id}/adjuntos
GET   /api/v1/soporte/tickets/{id}/historial
```

## Integraciones pendientes antes de producción

Este corte implementa el dominio, la autorización y la trazabilidad, pero no
incluye credenciales ni adaptadores específicos de proveedores. Antes de salir
a producción deben conectarse:

- almacenamiento privado de archivos y generación de URL de descarga firmada;
- proveedor de correo, SMS, WhatsApp o push;
- webhooks autenticados para confirmar entrega o fallo;
- antivirus y cuarentena para cargas de archivos;
- calendario externo, si el cliente desea sincronización fuera de la
  plataforma.

Estas integraciones pertenecen a infraestructura y no deben simularse mediante
un cambio manual de estado en producción.

## Verificación efectuada en este entorno

- 339 archivos Java de `src` fueron aceptados por un parser de gramática Java.
- 14 migraciones y 177 sentencias SQL fueron revisadas estructuralmente.
- 96 tablas, 58 entidades y 499 restricciones fueron verificadas.
- Ninguna entidad apunta a una tabla o columna inexistente.
- Los 78 permisos usados por controladores existen en la migración de permisos.
- Las 151 rutas HTTP son únicas.
- `V1–V13` conservaron sus contenidos y checksums.

No se ejecutó `mvn clean verify` ni una prueba integral contra MySQL porque el
entorno actual no dispone de JDK 21, Maven, Docker ni MySQL. Antes de integrar:

```bash
mvn clean verify
docker compose up --build
```

Debe probarse con dos sesiones concurrentes y cuentas reales de ensayo para los
roles `CLIENTE`, `SERVICIO_CLIENTE` y `ADMINISTRADOR`.
