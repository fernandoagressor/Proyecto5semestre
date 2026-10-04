# Quinto corte: Cartera y Pagos

Fecha: 31 de julio de 2026  
Repositorio: `inmobiliaria-api`  
Base: Java 21, Spring Boot 3.5.16, MySQL 8.4 y Flyway

## Alcance implementado

El módulo cubre el ciclo financiero posterior a la firma del contrato:

1. Creación y activación del plan de pagos.
2. Generación del cronograma de cuotas con capital e interés.
3. Registro, revisión, confirmación y rechazo de recaudos.
4. Aplicación exacta de pagos a cuotas y facturas.
5. Reversión trazable con restauración de saldos.
6. Conciliación de movimientos bancarios.
7. Cálculo incremental de días e interés de mora, sin reducir intereses ya
   causados cuando se recibe un abono parcial.
8. Gestiones y promesas de pago.
9. Propuesta, aprobación y aplicación de refinanciaciones.
10. Estados de cuenta y paz y salvos.
11. Cierres mensuales reproducibles.

## Reglas financieras críticas

- Solo un pago en estado `CONFIRMADO` afecta cuotas y facturas.
- La suma de las aplicaciones debe ser idéntica al valor del pago.
- El pago, sus cuotas y sus facturas se bloquean durante la confirmación.
- Una referencia bancaria no puede repetirse dentro de la misma empresa.
- Una reversión restaura cada saldo afectado y nunca elimina el pago.
- Quien solicita una reversión no puede aprobarla.
- Solo existe un plan `ACTIVO` por contrato.
- Un plan inicial debe coincidir con el número de cuotas del contrato vigente.
- La refinanciación exige aprobación independiente y no se aplica si el saldo
  cambió después de la propuesta.
- Un movimiento bancario solo puede asociarse con un pago confirmado del mismo
  periodo y un pago no puede conciliarse dos veces.
- El paz y salvo solo se emite cuando capital e intereses de mora son cero.
- Estados de cuenta y paz y salvos se emiten con la fecha actual para evitar
  presentar saldos históricos reconstruidos de forma incompleta.
- Un periodo cerrado no admite pagos con fecha retroactiva dentro del mismo.
- Un cierre debe ejecutarse antes de registrar pagos o planes del periodo
  siguiente; de lo contrario se rechaza para no alterar el corte histórico.
- Pagos, contratos, cierres y evidencias nunca se eliminan físicamente.

## Migración `V13`

`V13__integridad_cartera_pagos.sql` agrega, sin modificar `V1–V12`:

- control optimista de concurrencia en planes, cuotas, facturas, pagos,
  reversiones, conciliaciones y refinanciaciones;
- tasa efectiva anual de mora por plan;
- restricción de un único plan activo por contrato;
- restricción de una única reversión activa por pago;
- restricción de un único movimiento conciliado por pago;
- vínculo del plan generado por una refinanciación;
- validaciones de tasas, importes, saldos y periodos de cierre;
- índice operativo para el procesamiento de mora.

## Endpoints añadidos

### Planes y cuotas

```text
GET   /api/v1/cartera/planes-pago?contratoId={id}
POST  /api/v1/cartera/planes-pago
PATCH /api/v1/cartera/planes-pago/{planId}/activar
```

### Pagos y reversiones

```text
GET   /api/v1/pagos
POST  /api/v1/pagos
PATCH /api/v1/pagos/{pagoId}/revision
PATCH /api/v1/pagos/{pagoId}/confirmar
PATCH /api/v1/pagos/{pagoId}/rechazar
POST  /api/v1/pagos/{pagoId}/reversiones
PATCH /api/v1/pagos/{pagoId}/reversiones/{reversionId}/aprobar
PATCH /api/v1/pagos/{pagoId}/reversiones/{reversionId}/rechazar
```

### Conciliación bancaria

```text
GET   /api/v1/conciliaciones-bancarias
POST  /api/v1/conciliaciones-bancarias
POST  /api/v1/conciliaciones-bancarias/{id}/movimientos
PATCH /api/v1/conciliaciones-bancarias/{id}/movimientos/{movimientoId}/conciliar
PATCH /api/v1/conciliaciones-bancarias/{id}/movimientos/{movimientoId}/ignorar
PATCH /api/v1/conciliaciones-bancarias/{id}/cerrar
```

### Mora, refinanciación y documentos

```text
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
```

## Permisos utilizados

El corte reutiliza los permisos sembrados en `V9`: `CUOTAS_VER`,
`CUOTAS_PROGRAMAR`, `PAGOS_VER`, `PAGOS_CREAR`, `PAGOS_CONFIRMAR`,
`PAGOS_RECHAZAR`, `PAGOS_REVERSAR`, `CONCILIACION_BANCARIA`, `MORA_VER`,
`MORA_GESTIONAR`, `ESTADOS_CUENTA_GENERAR`, `REFINANCIACIONES_VER`,
`REFINANCIACIONES_PROPONER`, `REFINANCIACIONES_APROBAR`,
`PAZ_SALVO_GENERAR` y `CIERRE_CARTERA`.

## Automatización

El proceso `CarteraMoraJob` se ejecuta diariamente a la 1:15 a. m. en la zona
`America/Bogota` y causa la mora hasta el día inmediatamente anterior. La fecha
del último cálculo queda guardada por cuota para impedir doble causación. Puede
cambiarse con:

```text
app.cartera.mora-cron
```

## Verificación efectuada en este entorno

- Los 274 archivos Java de `src` fueron analizados con un parser de gramática
  Java sin errores sintácticos.
- Las 13 migraciones contienen 162 sentencias; 149 sentencias operativas fueron
  aceptadas por un parser MySQL y las 13 restantes son la instrucción común
  `SET NAMES utf8mb4`.
- Todas las entidades apuntan a tablas y columnas existentes.
- Todos los permisos usados por los controladores existen en `V9`.
- `V1–V12` conservaron sus contenidos y checksums.

No se ejecutó `mvn clean verify` ni una prueba integral contra MySQL porque el
entorno de generación dispone de JDK 17 y no incluye Maven, Docker ni MySQL. La
ejecución obligatoria antes de integrar es:

```bash
mvn clean verify
docker compose up --build
```

Después debe probarse el flujo completo con dos sesiones concurrentes y datos
de ensayo antes de usar información financiera real.
