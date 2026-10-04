# Séptimo corte: Gerencia y Control

Fecha: 31 de julio de 2026  
Repositorio: `inmobiliaria-api`  
Base: Java 21, Spring Boot 3.5.16, MySQL 8.4 y Flyway

## Alcance implementado

El corte agrega la capa ejecutiva y de control sobre los módulos operativos ya
construidos:

1. Dashboards comercial, cartera, servicio al cliente y gerencial.
2. Metas comerciales por empresa, proyecto o asesor.
3. Reglas de comisión globales o por proyecto.
4. Causación, aprobación, pago y anulación de comisiones.
5. Tareas manuales o automáticas con prioridad, asignación e historial.
6. Categorías de costo, presupuestos y costos reales.
7. Rentabilidad y desviación presupuestal por periodo.
8. Solicitudes de reportes con seguimiento de estado.
9. Consulta detallada de la bitácora de auditoría.
10. Asignación, investigación y resolución de alertas de anomalía.
11. Solicitudes de exportación de evidencia de auditoría.

Los indicadores no se almacenan como cifras editables. Cada consulta se calcula
desde contratos, pagos confirmados, cuotas, lotes, oportunidades, reservas,
costos, tareas, PQRS, citas y encuestas reales.

## Reglas críticas

- Todo acceso conserva el aislamiento por `empresa_id`.
- Un usuario con alcance parcial debe indicar un proyecto cuando tiene más de
  un proyecto asignado.
- Los indicadores comerciales permiten alcance de registros propios para el
  asesor autenticado.
- Una regla global de comisión requiere alcance completo de empresa.
- El proyecto de una regla de comisión no puede modificarse después de crearla.
- Solo se proyectan comisiones sobre contratos `VIGENTE` o `FINALIZADO`.
- La base de la comisión no puede superar el valor total del contrato.
- La causación valida una condición real: contrato vigente, cuota inicial
  pagada o recaudo total.
- El asesor beneficiario no puede aprobar su propia comisión.
- Una comisión pagada no se elimina ni se anula; permanece como evidencia.
- Una meta aprobada no puede editarse.
- El asesor no puede aprobar su propia meta.
- Solo se cierra una meta de un periodo ya terminado.
- Una tarea completada o cancelada no puede reabrirse ni reasignarse.
- El vencimiento de tareas se actualiza automáticamente en la zona horaria
  `America/Bogota` y genera historial.
- Quien registra un costo no puede aprobarlo.
- Quien crea un presupuesto no puede aprobarlo.
- Solo costos `APROBADO` o `PAGADO` participan en la rentabilidad.
- Solo presupuestos `APROBADO` o `CERRADO` participan en la ejecución
  presupuestal.
- Los eventos de auditoría aceptan únicamente inserciones. La migración crea
  disparadores que rechazan `UPDATE` y `DELETE`.
- Las operaciones concurrentes sobre metas, comisiones, tareas, presupuestos,
  costos, reportes y alertas usan versión optimista.
- Un reporte solo queda `GENERADO` cuando se asocia con un archivo activo de la
  misma empresa; en caso contrario debe quedar `FALLIDO` con una causa.

## Migración `V15`

`V15__integridad_gerencia_control.sql` agrega, sin modificar `V1–V14`:

- columnas `version` para nueve agregados mutables;
- actor de decisión para el flujo genérico de aprobaciones;
- índices para metas, comisiones, tareas, reportes, presupuestos y costos;
- dos disparadores de inmutabilidad para `auditoria_eventos`;
- nueve permisos específicos y su asignación a roles base.

El esquema conserva 96 tablas y queda con 138 permisos.

## Endpoints añadidos

### Dashboards

```text
GET /api/v1/dashboards/gerencial
GET /api/v1/dashboards/comercial
GET /api/v1/dashboards/cartera
GET /api/v1/dashboards/servicio
```

### Comisiones

```text
GET   /api/v1/comisiones/reglas
POST  /api/v1/comisiones/reglas
PUT   /api/v1/comisiones/reglas/{id}
PATCH /api/v1/comisiones/reglas/{id}/cerrar

GET   /api/v1/comisiones
POST  /api/v1/comisiones
PATCH /api/v1/comisiones/{id}/causar
PATCH /api/v1/comisiones/{id}/aprobar
PATCH /api/v1/comisiones/{id}/pagar
PATCH /api/v1/comisiones/{id}/anular
```

### Metas

```text
GET   /api/v1/metas-comerciales
POST  /api/v1/metas-comerciales
PUT   /api/v1/metas-comerciales/{id}
PATCH /api/v1/metas-comerciales/{id}/aprobar
PATCH /api/v1/metas-comerciales/{id}/cerrar
GET   /api/v1/metas-comerciales/{id}/cumplimiento
```

### Tareas

```text
GET   /api/v1/tareas
POST  /api/v1/tareas
GET   /api/v1/tareas/{id}
PATCH /api/v1/tareas/{id}/estado
PATCH /api/v1/tareas/{id}/asignacion
GET   /api/v1/tareas/{id}/historial
```

### Presupuesto, costos y rentabilidad

```text
GET   /api/v1/gerencia/categorias-costo
POST  /api/v1/gerencia/categorias-costo
PATCH /api/v1/gerencia/categorias-costo/{id}/inactivar

GET   /api/v1/gerencia/presupuestos
POST  /api/v1/gerencia/presupuestos
PUT   /api/v1/gerencia/presupuestos/{id}
PATCH /api/v1/gerencia/presupuestos/{id}/aprobar
PATCH /api/v1/gerencia/presupuestos/{id}/cerrar
PATCH /api/v1/gerencia/presupuestos/{id}/anular

GET   /api/v1/gerencia/costos
POST  /api/v1/gerencia/costos
PATCH /api/v1/gerencia/costos/{id}/aprobar
PATCH /api/v1/gerencia/costos/{id}/pagar
PATCH /api/v1/gerencia/costos/{id}/anular

GET   /api/v1/gerencia/rentabilidad
```

### Reportes

```text
POST  /api/v1/reportes
GET   /api/v1/reportes
GET   /api/v1/reportes/{id}
PATCH /api/v1/reportes/{id}/completar
```

### Auditoría y anomalías

```text
GET   /api/v1/auditoria/eventos
GET   /api/v1/auditoria/eventos/{id}
GET   /api/v1/auditoria/alertas
PATCH /api/v1/auditoria/alertas/{id}/asignar
PATCH /api/v1/auditoria/alertas/{id}/resolver
POST  /api/v1/auditoria/exportaciones
GET   /api/v1/auditoria/exportaciones/{id}
```

En total se añaden 52 rutas y el proyecto queda con 203 rutas HTTP únicas.

## Generadores pendientes antes de producción

El dominio registra solicitudes y resultados de reportes, pero el trabajador
que construye el archivo todavía debe implementarse en infraestructura. Debe:

1. tomar un reporte `PENDIENTE` con bloqueo;
2. ejecutar una consulta parametrizada con el mismo alcance de seguridad;
3. generar CSV, XLSX o PDF sin fórmulas inyectables;
4. almacenar el archivo en un contenedor privado;
5. registrar su hash, tamaño y análisis antivirus en `archivos`;
6. completar el reporte o marcarlo fallido;
7. entregar una URL firmada de corta duración.

La exportación de auditoría debe ejecutarse en una cola separada, limitar el
rango temporal y registrar toda descarga sensible.

## Verificación efectuada en este entorno

- 389 archivos Java fueron aceptados por un analizador de gramática Java.
- 15 migraciones y 96 tablas fueron revisadas estructuralmente.
- 66 entidades apuntan a tablas y columnas existentes.
- 270 claves foráneas y 501 restricciones con nombre único.
- 138 permisos disponibles y todos los permisos de controladores resueltos.
- 203 rutas HTTP únicas, sin colisiones.
- `V1–V14` conservan exactamente sus contenidos y checksums del sexto corte.

No se ejecutó `mvn clean verify` ni una prueba integral contra MySQL porque el
entorno actual solo tiene JDK 17 y no incluye Maven, Docker ni MySQL. Antes de
integrar:

```bash
mvn clean verify
docker compose up --build
```

También deben probarse sesiones concurrentes para aprobación de comisión,
aprobación de costo, cierre de meta, procesamiento de reporte y resolución de
alerta.
