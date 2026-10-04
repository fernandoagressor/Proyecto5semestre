-- NEXORA CORE
-- Migracion V19: cartera operativa y generacion automatica de planes.
-- Compatible con MySQL 8. No modifica V1-V18.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

CREATE TABLE `configuraciones_cartera` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `tasa_efectiva_anual` DECIMAL(9,6) NOT NULL DEFAULT 0,
  `tasa_mora_efectiva_anual` DECIMAL(9,6) NOT NULL DEFAULT 0,
  `periodicidad` VARCHAR(20) NOT NULL DEFAULT 'MENSUAL',
  `dias_primera_cuota` INT UNSIGNED NOT NULL DEFAULT 30,
  `auto_generar_plan` TINYINT(1) NOT NULL DEFAULT 1,
  `version` BIGINT NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_configuraciones_cartera_empresa` UNIQUE (`empresa_id`),
  CONSTRAINT `fk_configuraciones_cartera_empresa`
    FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `ck_configuraciones_cartera_tasas`
    CHECK (
      `tasa_efectiva_anual` >= 0 AND `tasa_efectiva_anual` <= 1
      AND `tasa_mora_efectiva_anual` >= 0 AND `tasa_mora_efectiva_anual` <= 1
    ),
  CONSTRAINT `ck_configuraciones_cartera_periodicidad`
    CHECK (`periodicidad` IN ('MENSUAL','BIMESTRAL','TRIMESTRAL','SEMESTRAL','ANUAL')),
  CONSTRAINT `ck_configuraciones_cartera_dias`
    CHECK (`dias_primera_cuota` BETWEEN 0 AND 365)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Parametros empresariales para generacion automatica de cartera.';

INSERT INTO `configuraciones_cartera`
(`empresa_id`,`tasa_efectiva_anual`,`tasa_mora_efectiva_anual`,`periodicidad`,`dias_primera_cuota`,`auto_generar_plan`)
SELECT `id`, 0, 0, 'MENSUAL', 30, 1
FROM `empresas`;

ALTER TABLE `cuotas`
  ADD KEY `idx_cuotas_tablero_cartera`
    (`empresa_id`, `contrato_id`, `estado`, `fecha_vencimiento`);

ALTER TABLE `pagos`
  ADD KEY `idx_pagos_tablero_cartera`
    (`empresa_id`, `contrato_id`, `estado`, `fecha_pago`);

ALTER TABLE `gestiones_cobro`
  ADD KEY `idx_gestiones_cobro_tablero`
    (`empresa_id`, `contrato_id`, `fecha_gestion`);

-- El administrador de cada empresa puede completar el flujo operativo
-- mostrado por la nueva interfaz de Cartera. SUPER_ADMIN ya conserva todos
-- los permisos y el rol CARTERA los recibió en V9.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r
JOIN `permisos` p ON p.codigo IN (
  'PAGOS_REVERSAR',
  'CONCILIACION_BANCARIA',
  'PAZ_SALVO_GENERAR',
  'CIERRE_CARTERA'
)
WHERE r.codigo = 'ADMINISTRADOR'
  AND r.activo = 1
  AND p.activo = 1
ON DUPLICATE KEY UPDATE
  `concedido` = 1,
  `actualizado_en` = CURRENT_TIMESTAMP(6);
