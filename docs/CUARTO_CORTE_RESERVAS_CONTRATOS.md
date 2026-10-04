# Cuarto corte: Reservas y Contratos

Fecha: 31 de julio de 2026

## Objetivo

Implementar el tramo transaccional que convierte una oportunidad comercial en
una venta verificable:

```text
Oportunidad → Reserva → Pago confirmado → Contrato → Firmas válidas → Venta
```

El módulo trabaja sobre las siete tablas normalizadas creadas en `V4` y agrega
la migración acumulativa `V12`. No modifica `V1-V11` ni crea tablas paralelas.

## Alcance implementado

- Reserva de un lote vinculado a una oportunidad abierta.
- Bloqueo pesimista del lote antes de crear la reserva.
- Restricción única de una reserva vigente por lote en MySQL.
- Asociación opcional de una simulación enviada y vigente.
- Confirmación o rechazo del pago de reserva con permisos de Cartera.
- Comprobante opcional validado contra empresa y proyecto.
- Extensión controlada de la fecha de vencimiento.
- Cancelación con motivo y liberación del lote.
- Proceso programado para vencer reservas y liberar lotes automáticamente.
- Conversión de una reserva activa y pagada en contrato.
- Cálculo derivado de valor total y saldo financiado.
- Cliente principal, copropietarios, beneficiarios y apoderados.
- Versiones económicas y documentales del contrato.
- Un único documento vigente por contrato.
- Firmantes ordenados y eventos de firma con hash SHA-256.
- Activación automática cuando todos los firmantes están validados.
- Anulación trazable de borradores, negociaciones, pendientes y vigentes.

## Reglas transaccionales

### Reserva

- El `empresa_id` proviene del JWT y nunca del cuerpo de la solicitud.
- La oportunidad, el cliente, la simulación y el lote deben pertenecer al mismo
  alcance empresarial y de proyecto.
- Solo un lote `DISPONIBLE` puede pasar a `RESERVADO`.
- Vincular un lote a una oportunidad o simularlo no lo reserva.
- Una simulación solo se acepta al crear la reserva si está `ENVIADA`, vigente y
  corresponde a la misma oportunidad y lote.
- El lote permanece bloqueado mientras la reserva está `PENDIENTE_PAGO` o
  `ACTIVA`.
- Rechazo, cancelación o vencimiento liberan el lote dentro de la misma
  transacción.
- Solo Cartera, mediante `PAGOS_CONFIRMAR` o `PAGOS_RECHAZAR`, valida el pago.
- Solo una reserva `ACTIVA` con pago `CONFIRMADO` puede convertirse.

### Contrato

- La conversión conserva la relación con reserva, oportunidad, cliente y lote.
- El valor del lote se congela desde la simulación aceptada o desde el precio
  vigente del inventario.
- Si existe simulación, el descuento contractual no puede superar el aprobado.
- Sin una simulación aceptada, el contrato no admite descuentos.
- `valorTotal = valorLote + otrosConceptos - descuento`.
- `saldoFinanciado = valorTotal - cuotaInicial`.
- Los contratos editables son `BORRADOR` y `NEGOCIACION`.
- Para enviar a firma debe existir al menos un firmante y una versión documental
  vigente con `archivo_id`.
- Registrar una firma almacena método, proveedor, referencia, hash, IP, fecha y
  estado de validación.
- Una firma `PENDIENTE` o `INVALIDA` nunca activa la venta.
- Cuando todos los firmantes están `FIRMADA`, el contrato pasa a `VIGENTE`, el
  lote a `VENDIDO`, la oportunidad a `GANADA` y el prospecto a `CONVERTIDO`.
- La anulación de un contrato vigente devuelve el lote a `DISPONIBLE` y reabre
  la oportunidad. Toda anulación exige motivo, fecha y usuario.

## Estados

Reserva:

```text
PENDIENTE_PAGO → ACTIVA → CONVERTIDA
       │            │
       ├────────────┴──→ VENCIDA
       └───────────────→ CANCELADA
```

Contrato:

```text
BORRADOR → NEGOCIACION → PENDIENTE_FIRMA → VIGENTE → FINALIZADO
    └──────────┴──────────────┴──────────────→ ANULADO
```

## Endpoints incorporados

El corte agrega 18 operaciones HTTP:

| Grupo | Operaciones |
|---|---:|
| Reservas y pago de reserva | 7 |
| Conversión de reserva a contrato | 1 |
| Contratos, participantes y versiones | 6 |
| Envío, registro de firmas y anulación | 4 |

Rutas base:

```text
/api/v1/reservas
/api/v1/contratos
```

## Seguridad

Permisos utilizados:

```text
RESERVAS_VER
RESERVAS_CREAR
RESERVAS_EDITAR
RESERVAS_CANCELAR
RESERVAS_CONVERTIR_CONTRATO
PAGOS_CONFIRMAR
PAGOS_RECHAZAR
CONTRATOS_VER
CONTRATOS_EDITAR
CONTRATOS_ANULAR
CONTRATOS_FIRMAS_GESTIONAR
```

Cada servicio vuelve a comprobar empresa, proyecto y asesor. Las consultas
paginadas respetan alcances `EMPRESA`, `PROYECTO` y `PROPIOS`.

## Integridad y concurrencia

`V12__integridad_reservas_contratos.sql` agrega:

- versión optimista a reservas y firmantes;
- comprobante y trazabilidad de confirmación del pago;
- trazabilidad de la anulación contractual;
- verificación exacta de totales y saldos;
- catálogo de tipos contractuales;
- una única versión documental vigente;
- validación de orden, fecha y hash de firmas;
- índices y claves foráneas para los nuevos campos.

Los bloqueos pesimistas se toman en el orden reserva/contrato, oportunidad y
lote para evitar que dos operaciones vendan la misma unidad. Las restricciones
únicas de MySQL actúan como segunda barrera y los conflictos se responden con
HTTP `409`.

## Límites de este corte

- Los archivos se referencian por metadatos ya registrados en `archivos`; el
  almacenamiento físico se conectará en la etapa de documentos/portal.
- El módulo registra firmas manuscritas, electrónicas o digitales y su
  evidencia, pero no implementa un proveedor propio de firma digital
  certificada.
- El plan de pagos contractual, cuotas, recaudos, mora y conciliación se
  implementarán en el siguiente corte de Cartera y Pagos.
- Antes de permitir la anulación de un contrato con recaudos confirmados, la
  siguiente etapa añadirá la validación y reversión financiera correspondiente.

## Pruebas y validación

- Transiciones de reserva, pago, vencimiento y rechazo.
- Cálculo derivado de total y saldo contractual.
- Prohibición de activar un contrato antes del envío a firma.
- Anulación con trazabilidad.
- Firma válida y cierre del firmante.
- Los 192 archivos Java pasan análisis sintáctico.
- Las 12 migraciones y sus 127 sentencias pasan análisis MySQL.
- Las 27 entidades JPA apuntan a tablas existentes.
- Los 41 permisos usados por los controladores existen en `V9`; la autoridad
  adicional `ROLE_SUPER_ADMIN` corresponde al rol protegido del sistema.
- Las restricciones conservan nombres únicos.
- `V1-V11` conservan exactamente sus contenidos originales.

La compilación Maven, el arranque de Spring y las pruebas integrales continúan
pendientes de un entorno con JDK 21, Maven 3.9 y MySQL 8.4.

## Siguiente etapa

```text
Cartera y Pagos
```

Incluirá plan de pagos, cuotas, recaudos, aplicación de pagos, mora,
conciliación, refinanciaciones, estados de cuenta y paz y salvo.
