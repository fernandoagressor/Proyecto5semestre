# Tercer corte: CRM Comercial y Simulaciones

Fecha: 31 de julio de 2026

## Objetivo

Implementar el flujo comercial que conecta el inventario con la futura venta:

```text
Prospecto → Oportunidad → Lote de interés → Simulación
                       ↘ Actividad / Cita
```

El módulo opera sobre ocho de las nueve tablas creadas en `V3`. La tabla
`presentaciones_comerciales` queda preparada para la futura evidencia de
materiales compartidos. `V11` incorpora restricciones operativas y columnas de
versión, sin duplicar el modelo normalizado.

## Alcance implementado

- Registro, consulta paginada y edición de prospectos.
- Detección de prospectos activos con correo o teléfono repetido.
- Calificación por estado y temperatura.
- Reasignación controlada de asesor.
- Creación y consulta paginada de oportunidades.
- Embudo comercial con probabilidad sugerida por etapa.
- Historial inmutable de cambios de etapa.
- Cierre perdido con motivo y reapertura con permiso sensible.
- Vinculación de hasta diez lotes disponibles por oportunidad.
- Selección de un lote principal sin reservarlo todavía.
- Registro cronológico de llamadas, mensajes, correos, visitas y propuestas.
- Programación, confirmación, reprogramación y cierre de citas.
- Detección de cruces de agenda para el mismo asesor.
- Agenda global filtrada por rango, estado y asesor.
- Simulaciones en COP con descuento, cuota inicial, financiación, TEA y
  periodicidad.
- Generación automática del cronograma de capital, interés, total y saldo.
- Envío y cancelación controlada de simulaciones.

No se cambia el estado del lote al crear una oportunidad o simulación. El lote
solo se reservará en la siguiente etapa, mediante una transacción específica
del módulo de reservas.

## Seguridad y aislamiento

Cada operación aplica dos comprobaciones:

1. El controlador exige el permiso granular correspondiente.
2. El servicio valida empresa, proyecto y alcance vigente.

Los alcances CRM se interpretan así:

- `EMPRESA`: consulta y gestión de todos los registros de la empresa.
- `PROYECTO`: registros asociados a proyectos asignados.
- `PROPIOS`: registros cuyo asesor responsable es el usuario autenticado.

El `empresa_id` se obtiene del JWT. Los identificadores enviados por el
navegador se vuelven a validar contra la empresa y el proyecto.

## Endpoints

El corte incorpora 30 operaciones HTTP:

| Grupo | Operaciones |
|---|---:|
| Prospectos | 6 |
| Oportunidades, lotes e historial | 12 |
| Actividades, citas y agenda | 7 |
| Simulaciones | 5 |

Rutas principales:

```text
/api/v1/prospectos
/api/v1/oportunidades
/api/v1/agenda/comercial
/api/v1/simulaciones
```

Los permisos utilizados ya existen en `V9`:

```text
PROSPECTOS_VER
PROSPECTOS_CREAR
PROSPECTOS_EDITAR
PROSPECTOS_CALIFICAR
PROSPECTOS_REASIGNAR
OPORTUNIDADES_VER
OPORTUNIDADES_CREAR
OPORTUNIDADES_EDITAR
OPORTUNIDADES_AVANZAR
OPORTUNIDADES_CERRAR_PERDIDA
OPORTUNIDADES_REABRIR
AGENDA_VER
AGENDA_GESTIONAR
SIMULACIONES_VER
SIMULACIONES_CREAR
SIMULACIONES_ENVIAR
```

## Reglas del embudo

- Un prospecto necesita teléfono o correo.
- Un prospecto descartado requiere justificación.
- `CONVERTIDO` no se asigna manualmente; lo establecerá el proceso de venta.
- Solo un prospecto `CALIFICADO` puede originar una oportunidad.
- Un prospecto no puede tener dos oportunidades abiertas.
- Una oportunidad cerrada no admite edición, actividades ni lotes nuevos.
- `GANADA` no se asigna manualmente; dependerá de reserva/contrato.
- Cada cambio de etapa conserva etapa anterior, nueva, usuario y observación.
- Solo los lotes `DISPONIBLE` pueden vincularse o simularse.
- Vincular o seleccionar un lote no altera su disponibilidad.

## Reglas de agenda

- La fecha final debe ser posterior a la inicial.
- Las citas nuevas y reprogramadas deben estar en el futuro.
- Un asesor no puede tener dos citas activas superpuestas.
- Las citas realizadas o sin asistencia exigen un resultado.
- Una cita finalizada no puede reprogramarse.
- Una consulta global de agenda no puede superar 93 días.

## Reglas financieras

- Los cálculos se realizan con `BigDecimal` y redondeo monetario a dos
  decimales.
- La TEA se recibe como razón decimal: `0.12` representa 12 % efectivo anual.
- El descuento más la cuota inicial no puede superar el valor del lote.
- El saldo financiado se deriva del valor del lote, no se recibe del cliente.
- La última cuota ajusta el capital restante para cerrar el saldo en cero.
- La simulación es informativa; no crea cuotas contractuales ni reserva.
- Una simulación vencida no puede enviarse.

Ejemplo:

```json
{
  "oportunidadId": 18,
  "loteId": 42,
  "descuento": 5000000.00,
  "cuotaInicial": 25000000.00,
  "numeroCuotas": 36,
  "tasaEfectivaAnual": 0.120000,
  "periodicidad": "MENSUAL",
  "fechaPrimeraCuota": "2026-09-15",
  "vigenteHasta": "2026-08-15T23:59:59"
}
```

## Integridad y concurrencia

`V11__integridad_operativa_crm.sql` agrega:

- `version` a prospectos, oportunidades, citas y simulaciones.
- Catálogos válidos de fuente, etapa y periodicidad.
- Restricciones de contacto y cierre.
- Coherencia entre valor, descuento, cuota inicial y saldo financiado.
- Coherencia entre componentes y total de cada cuota.
- Índice para la selección de lotes por oportunidad.

Las operaciones sensibles recuperan oportunidades y simulaciones con bloqueo
pesimista. Los conflictos optimistas e incumplimientos de integridad se
devuelven como HTTP `409`.

## Pruebas incorporadas

- Motivo obligatorio al descartar prospectos.
- Prohibición de conversión manual.
- Cambio de etapa y probabilidad sugerida.
- Cierre perdido y bloqueo posterior.
- Reprogramación y resultado obligatorio de citas.
- Plan sin interés con saldo final cero.
- Plan con TEA y fechas periódicas.

## Validación de este entorno

- Los 142 archivos Java del repositorio pasan análisis sintáctico.
- Las 11 migraciones y sus 116 sentencias pasan análisis MySQL.
- Las 19 entidades JPA apuntan a tablas existentes.
- Los 16 permisos utilizados por CRM existen en `V9`.
- Las restricciones tienen nombres únicos.
- Los archivos `V1–V10` conservan exactamente sus contenidos y checksums.

La compilación Maven, el arranque de Spring y las pruebas de integración deben
ejecutarse en CI o en Docker con JDK 21, Maven 3.9 y MySQL 8.4. El entorno
actual solo dispone de JDK 17 y no incluye Maven ni Docker.

## Siguiente etapa

El siguiente módulo es:

```text
Reservas y Contratos
```

Debe reservar el lote de forma transaccional, controlar vigencia y
concurrencia, convertir la reserva en contrato y marcar la oportunidad como
ganada únicamente cuando corresponda.
