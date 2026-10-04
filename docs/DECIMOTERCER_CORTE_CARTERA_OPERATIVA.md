# Decimotercer corte R2: Cartera operativa

Fecha: 12 de agosto de 2026  
Base: Corte 12 R4 validado por el usuario  
Stack: Java 21, Spring Boot 3.5.16, MySQL 8.4 y Angular 20.3

## Objetivo

Convertir el núcleo financiero ya existente en un módulo operativo conectado
al flujo validado de Ventas:

```text
Contrato firmado -> plan activo -> cuotas -> recaudo -> mora -> cobranza
```

## Funcionalidad incorporada

- Generación automática del plan cuando todas las firmas válidas activan el
  contrato.
- Sincronización idempotente para contratos que ya estaban vigentes antes de
  instalar este corte.
- Configuración por empresa de tasa efectiva anual, tasa de mora,
  periodicidad, días hasta la primera cuota y activación automática.
- Tablero de cartera con total programado, recaudo confirmado, saldo,
  vencimiento, porcentaje de mora, próximos vencimientos y pagos por revisar.
- Bandeja de contratos con cliente, proyecto, lote, cuota inicial, capital
  financiado y estado del cronograma.
- Estado de cuenta por contrato y detalle completo de cada cuota.
- Registro de pagos totales, parciales o anticipados.
- Distribución automática del abono a las obligaciones más antiguas, aplicando
  primero mora y luego capital.
- Confirmación inmediata para usuarios autorizados o envío a revisión cuando
  el usuario solo puede registrar pagos.
- Rechazo trazable y solicitud de reversión con aprobación independiente.
- Consulta y actualización de cuotas vencidas.
- Gestiones de cobranza por llamada, WhatsApp, correo, SMS o visita, incluyendo
  promesa de fecha y valor.
- Exportación CSV del consolidado y del estado de cuenta individual.
- Interfaz responsive filtrada por permisos y alcance de empresa, proyecto o
  registros propios.

## Migración V19

`V19__cartera_operativa_y_generacion_automatica.sql` agrega:

- tabla `configuraciones_cartera`, una fila por empresa;
- valores iniciales conservadores: tasa ordinaria 0, tasa de mora 0,
  periodicidad mensual y primera cuota a 30 días;
- generación automática habilitada;
- índices para el tablero de cuotas, pagos y gestiones de cobro;
- permisos operativos adicionales para el rol `ADMINISTRADOR`.

Las migraciones `V1-V18` no se modifican. Al iniciar el backend con la misma
base de datos, Flyway ejecuta solamente `V19`.

## Endpoints nuevos

```text
GET  /api/v1/cartera/resumen
GET  /api/v1/cartera/contratos
GET  /api/v1/cartera/metodos-pago
GET  /api/v1/cartera/configuracion
PUT  /api/v1/cartera/configuracion
POST /api/v1/cartera/planes-pago/contratos/{contratoId}/sincronizar
```

Se reutilizan los endpoints ya protegidos de planes, pagos, mora, cobranza,
refinanciación, conciliación, estados de cuenta y cierres.

## Reglas conservadas

- Solo un plan activo por contrato.
- Solo pagos confirmados afectan saldos.
- Las aplicaciones suman exactamente el valor del pago.
- Las referencias bancarias no se repiten dentro de la empresa.
- Una reversión nunca elimina el pago ni su evidencia.
- El solicitante de una reversión no puede aprobarla.
- Los registros respetan empresa y alcance del usuario autenticado.

## Uso inicial con el contrato ya firmado

1. Iniciar backend y permitir que Flyway aplique `V19`.
2. Entrar en **Cartera**.
3. Abrir el contrato vigente.
4. Pulsar **Generar cartera**.
5. Revisar el cronograma antes de registrar el primer abono.

Los contratos que se firmen después de instalar este corte aparecerán con el
plan activo automáticamente.

## Verificación

- Compilación de producción Angular correcta.
- 33 pruebas Angular aprobadas.
- Revisión de integridad de la migración y de las referencias de tabla/columna.
- Paquete final sin `node_modules`, `dist`, `target`, `storage` ni credenciales.

## Revisión R1: cuotas COP enteras

- Los cronogramas nuevos distribuyen capital, interés y mora en pesos enteros.
- La diferencia de la división se acumula en la última cuota para conservar
  exactamente el capital financiado.
- El formulario de abonos acepta valores enteros, coherentes con la
  presentación en COP.
- `V20__cuotas_cop_sin_centavos.sql` corrige de forma automática los planes
  activos a tasa cero generados previamente, cuando la corrección es segura.
- Una cuota con saldo oculto inferior a un peso queda `PAGADA` si el abono en
  pesos ya cubrió su nuevo valor programado.
- Las migraciones `V1-V19` permanecen intactas.

## Revisión R2: seguimiento preventivo y cobranza

- La pestaña **Mora y cobro** incluye cuotas `PENDIENTES`, `PARCIALES` y
  `VENCIDAS` con saldo, ordenadas por fecha de vencimiento.
- Las cuotas futuras muestran los días restantes y permiten registrar una
  gestión preventiva antes de entrar en mora.
- Las cuotas vencidas conservan días e interés de mora y continúan usando el
  flujo de cobranza.
- Las llamadas, mensajes, correos, visitas y promesas quedan vinculadas a la
  cuota seleccionada y conservan el historial del contrato.
- Una promesa requiere fecha y valor completos, no admite fechas pasadas y no
  puede superar el saldo pendiente de la cuota.
- El backend vuelve a comprobar que la cuota pertenezca al contrato, esté en
  un estado gestionable y conserve saldo al momento de guardar.
- Nuevo endpoint protegido:

```text
GET /api/v1/cartera/seguimiento
```

- No se agrega migración: `V1-V20` permanecen intactas y los abonos ya
  registrados no se modifican.
- 35 pruebas Angular aprobadas, incluida la interacción real de una gestión
  preventiva sobre una cuota parcial.

## Revisión R3: historial visible de gestiones

- Después de registrar una gestión, el modal permanece abierto y cambia
  inmediatamente al historial de la cuota seleccionada.
- Cada fila de **Mora y cobro** incorpora la acción **Ver historial** para
  consultar nuevamente las gestiones persistidas, incluso después de recargar.
- El historial muestra canal, resultado, fecha, observaciones y, cuando existe,
  la fecha y el valor de la promesa de pago.
- La consulta se limita a la cuota seleccionada y conserva el control de alcance
  existente por empresa, proyecto o registros propios.
- No se agrega migración: `V1-V20` permanecen intactas y no se modifican planes,
  pagos, saldos ni gestiones ya registradas.
