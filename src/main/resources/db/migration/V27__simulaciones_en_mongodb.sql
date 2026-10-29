-- Rama 8b: las simulaciones de financiación (con su cronograma de cuotas embebido) pasan a la
-- colección simulaciones de MongoDB.
--
-- 1. Se suelta la llave reservas -> simulaciones: las simulaciones nuevas solo existen en MongoDB.
--    ReservaService valida la simulación contra MongoDB.
-- 2. crm_pendientes gana simulacion_id y el tipo SIMULACION_ACEPTADA: la reserva (MySQL) marca la
--    simulación como ACEPTADA en MongoDB al confirmarse, por la misma cola que el resto del CRM.
--
-- Las tablas simulaciones y simulacion_cuotas se conservan sin escrituras como respaldo hasta la
-- rama de retiro. MigradorSimulaciones copia su histórico a MongoDB al arrancar.

SET NAMES utf8mb4;

ALTER TABLE `reservas`
  DROP FOREIGN KEY `fk_reservas_simulacion_id_simulaciones`;

ALTER TABLE `crm_pendientes`
  ADD COLUMN `simulacion_id` BIGINT UNSIGNED NULL AFTER `contrato_id`,
  DROP CHECK `ck_crm_pendientes_tipo`;

ALTER TABLE `crm_pendientes`
  ADD CONSTRAINT `ck_crm_pendientes_tipo`
    CHECK (`tipo` IN ('CLIENTE_RESERVA','VENTA_GANADA','VENTA_ANULADA','SIMULACION_ACEPTADA'));
