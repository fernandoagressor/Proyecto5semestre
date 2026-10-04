-- Plataforma de Gestión Inmobiliaria
-- Migración V13: integridad operativa de cartera y pagos.
-- Compatible con MySQL 8.
-- No modifica las migraciones V1-V12.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- =========================================================
-- PLANES DE PAGO
-- =========================================================

ALTER TABLE `planes_pago`
    ADD COLUMN `tasa_mora_efectiva_anual` DECIMAL(9,6)
        NOT NULL DEFAULT 0
    AFTER `tasa_efectiva_anual`,

  ADD COLUMN `version` BIGINT
    NOT NULL DEFAULT 0
    AFTER `origen`,

  ADD CONSTRAINT `ck_planes_pago_tasas`
    CHECK (
      `tasa_efectiva_anual` >= 0
      AND `tasa_efectiva_anual` <= 1
      AND `tasa_mora_efectiva_anual` >= 0
      AND `tasa_mora_efectiva_anual` <= 1
    );

-- La regla "un solo plan activo por contrato" debe validarse
-- en el servicio, debido a la FK contrato_id con ON DELETE CASCADE.

-- =========================================================
-- CUOTAS
-- =========================================================

ALTER TABLE `cuotas`
    ADD COLUMN `mora_calculada_hasta` DATE
    AFTER `interes_mora`,

  ADD COLUMN `version` BIGINT
    NOT NULL DEFAULT 0
    AFTER `estado`,

  ADD KEY `idx_cuotas_mora_operativa`
    (`empresa_id`, `fecha_vencimiento`, `saldo`, `estado`),

  ADD CONSTRAINT `ck_cuotas_consistencia_valores`
    CHECK (
      `capital` >= 0
      AND `interes` >= 0
      AND `otros_conceptos` >= 0
      AND `interes_mora` >= 0
      AND `valor_pagado` <= `valor_programado`
    );

-- =========================================================
-- FACTURAS
-- =========================================================

ALTER TABLE `facturas`
    ADD COLUMN `version` BIGINT
        NOT NULL DEFAULT 0
    AFTER `archivo_id`,

  ADD CONSTRAINT `ck_facturas_saldo_total`
    CHECK (
      `saldo` >= 0
      AND `saldo` <= `valor_total`
    );

-- =========================================================
-- PAGOS
-- =========================================================

ALTER TABLE `pagos`
    ADD COLUMN `version` BIGINT
        NOT NULL DEFAULT 0
    AFTER `observaciones`;

-- La consistencia entre estado, validado_por y validado_en se
-- valida en el backend. validado_por posee FK ON DELETE SET NULL
-- y MySQL no permite usarla en este CHECK.

-- =========================================================
-- REVERSIONES DE PAGOS
-- =========================================================

ALTER TABLE `pago_reversiones`
    ADD COLUMN `solicitud_activa_pago_id` BIGINT UNSIGNED
    GENERATED ALWAYS AS (
      CASE
        WHEN `estado` IN ('SOLICITADA', 'APROBADA')
          THEN `pago_id`
        ELSE NULL
      END
    ) STORED
    AFTER `estado`,

  ADD COLUMN `version` BIGINT
    NOT NULL DEFAULT 0
    AFTER `ejecutada_en`,

  ADD CONSTRAINT `uk_pago_reversiones_una_activa`
    UNIQUE (`solicitud_activa_pago_id`);

-- =========================================================
-- CONCILIACIONES BANCARIAS
-- =========================================================

ALTER TABLE `conciliaciones_bancarias`
    ADD COLUMN `version` BIGINT
        NOT NULL DEFAULT 0
    AFTER `cerrada_en`,

  ADD CONSTRAINT `ck_conciliaciones_totales`
    CHECK (
      `saldo_extracto` >= 0
      AND `total_conciliado` >= 0
    );

-- =========================================================
-- MOVIMIENTOS DE CONCILIACIÓN
-- =========================================================

ALTER TABLE `conciliacion_movimientos`
    ADD COLUMN `version` BIGINT
        NOT NULL DEFAULT 0
    AFTER `diferencia`;

-- Se elimina pago_vinculado_id porque dependía de pago_id,
-- cuya FK utiliza ON DELETE SET NULL.
-- La unicidad del pago conciliado debe controlarse en el servicio.

-- =========================================================
-- REFINANCIACIONES
-- =========================================================

ALTER TABLE `refinanciaciones`
    ADD COLUMN `plan_pago_generado_id` BIGINT UNSIGNED
    AFTER `fecha_decision`,

  ADD COLUMN `version` BIGINT
    NOT NULL DEFAULT 0
    AFTER `plan_pago_generado_id`,

  ADD KEY `idx_refinanciaciones_plan_pago_generado_id`
    (`plan_pago_generado_id`),

  ADD CONSTRAINT `fk_refinanciaciones_plan_pago_generado`
    FOREIGN KEY (`plan_pago_generado_id`)
    REFERENCES `planes_pago` (`id`)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,

                                                                       ADD CONSTRAINT `ck_refinanciaciones_tasa`
                                                                       CHECK (
                                                                       `tasa_nueva` >= 0
                                                                       AND `tasa_nueva` <= 1
                                                                       );

-- =========================================================
-- CIERRES DE CARTERA
-- =========================================================

ALTER TABLE `cierres_cartera`
    ADD CONSTRAINT `ck_cierres_cartera_periodo`
        CHECK (
            `periodo` REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
    ),

    ADD CONSTRAINT `ck_cierres_cartera_valores`
    CHECK (
    `saldo_total` >= 0
    AND `saldo_vencido` >= 0
    AND `saldo_vencido` <= `saldo_total`
    AND `recaudo_periodo` >= 0
    AND `indice_mora` >= 0
    AND `indice_mora` <= 1
    );