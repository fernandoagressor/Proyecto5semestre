# Duodécimo corte: centro operativo de ventas

## Revisión R4: confirmación de pago por clic explícito

- El modal de pago ya no depende del envío nativo de un formulario.
- El botón ejecuta directamente la carga del comprobante y la confirmación de la reserva.
- Mientras se procesa, el modal permanece abierto, muestra `Confirmando…` y bloquea cierres o dobles clics.
- Al finalizar, la reserva cambia a `ACTIVA / CONFIRMADO` y habilita la creación del contrato.
- No requiere cambios de backend, datos ni migraciones; `V1–V18` permanecen intactas.

Este corte parte del Corte 11 aprobado. Las migraciones `V1` a `V17` se
conservan sin cambios y `V18__permisos_operacion_ventas.sql` es la única
migración nueva.

## Corrección R1 del CRM

- Se corrigieron las consultas nativas que validan contactos duplicados y
  cruces de agenda. MySQL devuelve `COUNT(*)` como número, por lo que ahora el
  servicio compara explícitamente el conteo y evita conversiones inválidas de
  `Long` a `Boolean`.
- La corrección no cambia el esquema, los datos ni las migraciones `V1` a
  `V18`.

## Revisión R2: activación y lotes desde la aplicación

- La pantalla **Proyectos** permite activar un proyecto en `PLANEACION`,
  `PAUSADO` o `INACTIVO` cuando el usuario posee `PROYECTOS_EDITAR`.
- Cada oportunidad abierta del pipeline incorpora la acción **Vincular lote**.
- El panel de la oportunidad muestra sus lotes vinculados, permite agregar
  únicamente lotes `DISPONIBLE` del mismo proyecto, marcar uno como preferido
  y quitar relaciones que ya no correspondan.
- En **Ventas > Nueva reserva**, el lote preferido aparece primero y solo se
  ofrecen lotes vinculados que continúan en estado `DISPONIBLE`.
- R2 reutiliza los endpoints existentes, no incorpora SQL nuevo y conserva
  intactas las migraciones `V1` a `V18`.

## Revisión R3: actualización inmediata después del pago

- Al confirmar un pago con comprobante, el detalle abierto adopta
  inmediatamente la reserva devuelta por el backend.
- La reserva cambia en pantalla a `ACTIVA` y el pago a `CONFIRMADO` sin exigir
  recarga manual ni un segundo intento.
- La acción **Crear contrato** queda disponible en el mismo detalle cuando el
  usuario posee `RESERVAS_CONVERTIR_CONTRATO`.
- La lista se sincroniza en segundo plano y conserva los filtros activos.
- R3 solo corrige el estado de la interfaz; el backend y las migraciones `V1`
  a `V18` permanecen idénticos a R2.

## Funciones incorporadas

- Maestro de clientes con alta, consulta, edición, activación e inactivación.
- Reservas desde oportunidades abiertas con lotes previamente vinculados.
- Bloqueo transaccional del lote al crear la reserva.
- Carga privada del comprobante y confirmación o rechazo del pago.
- Extensión, cancelación, vencimiento e historial de la reserva.
- Conversión de una reserva activa en contrato.
- Expediente contractual con participantes, versiones y firmantes.
- Carga y descarga de PDF, PNG y JPG de hasta 15 MB.
- Huella SHA-256 para cada evidencia almacenada.
- Registro de firmas válidas y activación automática de la venta.
- Anulación trazable con liberación o reversión controlada del lote.

## Almacenamiento documental

En ejecución local los documentos se guardan por defecto en `./storage`. La
ruta puede cambiarse con `APP_STORAGE_PATH`. Docker Compose utiliza un volumen
persistente llamado `inmobiliaria_documentos`.

El backend valida empresa, proyecto, alcance del usuario, tipo MIME, tamaño y
clasificación antes de registrar un archivo. Las carpetas de almacenamiento no
forman parte del código fuente ni del ZIP.

## Prueba funcional recomendada

1. Iniciar el backend y comprobar que Flyway aplica únicamente `V18`.
2. Iniciar Angular con `npm start`.
3. Crear o seleccionar un cliente activo.
4. En CRM, abrir una oportunidad y vincularle un lote disponible.
5. En Ventas, crear la reserva y adjuntar un comprobante.
6. Confirmar el pago y convertir la reserva en contrato.
7. Adjuntar la versión contractual que se firmará.
8. Agregar participantes o firmantes cuando corresponda.
9. Enviar a firma y registrar la evidencia de cada firmante.
10. Confirmar que el contrato quede `VIGENTE`, el lote `VENDIDO` y la
    oportunidad `GANADA`.

## Reglas de despliegue

- No ejecutar `repair` ni editar migraciones históricas.
- Conservar y respaldar tanto MySQL como el volumen de documentos.
- Usar Java 21, Maven 3.9+, MySQL 8.0.16+ y Node compatible con Angular 20.
