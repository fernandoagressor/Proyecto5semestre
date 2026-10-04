# Noveno corte — Frontend operativo de Inventario y CRM

## Objetivo

Convertir los espacios reservados del octavo corte en interfaces operativas
conectadas a los contratos reales de `inmobiliaria-api`, sin modificar las
quince migraciones Flyway ni duplicar reglas de autorización en Angular.

## Inventario

La ruta `/inventario` ahora incluye:

- Selector de proyecto autorizado y enlace directo desde cada tarjeta del
  portafolio.
- Resumen de lotes totales, disponibles, reservados, vendidos, bloqueados y
  valor comercial disponible.
- Búsqueda con espera controlada y filtros por estado, etapa y manzana.
- Tabla paginada de lotes con área, dimensiones, precio y disponibilidad.
- Creación de etapas, manzanas y lotes mediante formularios validados.
- Cambio manual permitido únicamente entre `DISPONIBLE` y `BLOQUEADO`.
- Cambio de precio con motivo obligatorio.
- Consulta del historial de precio y estado de cada lote.
- Diseño responsive con vista de tabla, estados vacíos, carga y error.

Los estados `RESERVADO` y `VENDIDO` no se asignan desde la interfaz. Continúan
dependiendo de los flujos transaccionales de reserva, pago, contrato y firma.

## CRM comercial

La ruta `/crm` contiene cuatro vistas:

### Pipeline

- Ocho etapas desde prospecto recibido hasta cierre.
- Tarjetas de oportunidad con valor, probabilidad y próxima acción.
- Avance a la etapa siguiente mediante el endpoint controlado del backend.
- Accesos contextuales para agendar citas y crear simulaciones.

### Prospectos

- Bandeja con búsqueda, proyecto, fuente, presupuesto, temperatura y estado.
- Registro de contactos con fuente y observaciones.
- Calificación controlada.
- Conversión del prospecto en una oportunidad asociada al proyecto.

### Agenda

- Consulta mensual de citas comerciales.
- Registro de visitas, llamadas, videollamadas y reuniones.
- Validación de fecha de finalización en Angular.
- Validación definitiva de cruces y alcance en Spring Boot.

### Simulaciones

- Selección de oportunidad y lote disponible.
- Descuento, cuota inicial, plazo, tasa, periodicidad y vigencia.
- Generación del cronograma mediante la API.
- Envío controlado de simulaciones en estado borrador.
- La creación no cambia la disponibilidad del lote.

## Seguridad y alcances

- Las rutas continúan protegidas por `authGuard` y `permissionGuard`.
- Cada botón operativo requiere la autoridad correspondiente.
- Angular no envía ni decide `empresa_id`.
- El backend conserva la autoridad final sobre empresa, proyecto, asesor,
  disponibilidad, concurrencia y reglas de estado.
- Los servicios utilizan exclusivamente las rutas `/api/v1` existentes.

## Cambios técnicos

- Versión del frontend actualizada a `0.9.0`.
- Modelos TypeScript para Inventario y CRM.
- Servicios REST dedicados para ambos módulos.
- Dos páginas standalone con carga diferida.
- Corrección de los estados de proyecto del frontend a `PAUSADO` e `INACTIVO`,
  alineados con el enum Java.
- Navegación directa `Proyectos → Inventario` conservando el proyecto elegido.
- Cuatro pruebas nuevas para contratos HTTP de Inventario, agenda y pipeline.

## Validación

- Compilación Angular de producción completada sin errores ni advertencias.
- Paquete inicial: 312,64 kB sin comprimir y 88,16 kB estimados transferidos.
- Inventario y CRM permanecen como chunks lazy independientes.
- Diez pruebas unitarias aprobadas en cinco archivos.
- Ningún cambio en `src/main/resources/db/migration/V1__...` a `V15__...`.

No se ejecutaron pruebas E2E contra Spring Boot y MySQL reales en este corte.
Esas pruebas forman parte de la etapa de integración e infraestructura previa al
piloto.

## Próxima etapa

El siguiente corte del frontend corresponde a **Ventas y Cartera**:

1. Reservas y comprobantes.
2. Contratos, participantes, documentos y firmas.
3. Planes de pago, cuotas y recaudos.
4. Conciliación, mora, refinanciaciones y estados de cuenta.
