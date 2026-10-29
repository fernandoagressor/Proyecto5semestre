-- Rama 8: prospectos, oportunidades (con lotes de interés, historial, actividades y
-- presentaciones) y citas comerciales pasan a MongoDB.
--
-- 1. Se sueltan las llaves de MySQL que apuntaban a oportunidades: las oportunidades nuevas solo
--    existen en MongoDB. ReservaService, ContratoService y SimulacionService validan contra MongoDB.
-- 2. crm_pendientes: cola de cambios que la venta (MySQL) le hace al CRM (MongoDB). Se escribe en
--    la misma transacción de la venta y ProcesadorPendientesCrm la aplica en MongoDB.
-- 3. Se siembra lectura_oportunidades con el histórico para el dashboard comercial.
--
-- Las tablas del CRM se conservan sin escrituras como respaldo hasta la rama de retiro.

SET NAMES utf8mb4;

ALTER TABLE `reservas`
  DROP FOREIGN KEY `fk_reservas_oportunidad_id_oportunidades`;

ALTER TABLE `contratos`
  DROP FOREIGN KEY `fk_contratos_oportunidad_id_oportunidades`;

ALTER TABLE `simulaciones`
  DROP FOREIGN KEY `fk_simulaciones_oportunidad_id_oportunidades`;

CREATE TABLE `crm_pendientes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(30) NOT NULL,
  `cliente_id` BIGINT UNSIGNED NULL,
  `contrato_id` BIGINT UNSIGNED NULL,
  `usuario_id` BIGINT UNSIGNED NULL,
  `fecha` DATETIME(6) NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `procesado_en` DATETIME(6) NULL,
  `tomado_hasta` DATETIME(6) NULL,
  `intentos` INT UNSIGNED NOT NULL DEFAULT 0,
  `ultimo_error` VARCHAR(700) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_crm_pendientes_por_procesar` (`procesado_en`, `id`),
  KEY `idx_crm_pendientes_oportunidad` (`oportunidad_id`, `id`),
  CONSTRAINT `ck_crm_pendientes_tipo` CHECK (`tipo` IN ('CLIENTE_RESERVA','VENTA_GANADA','VENTA_ANULADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Cambios de la venta pendientes de aplicar al CRM en MongoDB.';

INSERT INTO `lectura_oportunidades`
  (`id`, `empresa_id`, `proyecto_id`, `asesor_id`, `etapa_actual`, `estado`, `probabilidad_cierre`,
   `valor_estimado`, `fecha_cierre`, `creado_en`)
SELECT o.`id`, o.`empresa_id`, o.`proyecto_id`, o.`asesor_id`, o.`etapa_actual`, o.`estado`,
       o.`probabilidad_cierre`, o.`valor_estimado`, o.`fecha_cierre`, o.`creado_en`
FROM `oportunidades` o;
