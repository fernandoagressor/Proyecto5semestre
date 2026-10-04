-- Plataforma de Gestión Inmobiliaria
-- Migración V12: integridad operativa de reservas y contratos.
-- No modifica las migraciones V1-V11.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

ALTER TABLE `reservas`
  ADD COLUMN `comprobante_archivo_id` BIGINT UNSIGNED
    AFTER `motivo_cancelacion`,
  ADD COLUMN `pago_confirmado_en` DATETIME(6)
    AFTER `comprobante_archivo_id`,
  ADD COLUMN `pago_confirmado_por` BIGINT UNSIGNED
    AFTER `pago_confirmado_en`,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0
    AFTER `pago_confirmado_por`,
  ADD CONSTRAINT `fk_reservas_comprobante_archivo_archivos`
    FOREIGN KEY (`comprobante_archivo_id`)
    REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_reservas_pago_confirmado_por_usuarios`
    FOREIGN KEY (`pago_confirmado_por`)
    REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  ADD KEY `idx_reservas_comprobante_archivo_id`
    (`comprobante_archivo_id`),
  ADD KEY `idx_reservas_pago_confirmado_por`
    (`pago_confirmado_por`);

UPDATE `reservas`
SET `pago_confirmado_en` = `actualizado_en`
WHERE `estado_pago` = 'CONFIRMADO'
  AND `pago_confirmado_en` IS NULL;

ALTER TABLE `reservas`
  ADD CONSTRAINT `ck_reservas_flujo_pago_estado`
    CHECK (
      (`estado` = 'PENDIENTE_PAGO' AND `estado_pago` = 'PENDIENTE')
      OR (`estado` = 'ACTIVA' AND `estado_pago` = 'CONFIRMADO')
      OR (`estado` = 'CONVERTIDA' AND `estado_pago` = 'CONFIRMADO')
      OR (`estado` IN ('VENCIDA','CANCELADA'))
    ),
  ADD CONSTRAINT `ck_reservas_confirmacion_pago`
    CHECK (
      (`estado_pago` = 'CONFIRMADO' AND `pago_confirmado_en` IS NOT NULL)
      OR (`estado_pago` <> 'CONFIRMADO' AND `pago_confirmado_en` IS NULL)
    );

ALTER TABLE `contratos`
  ADD COLUMN `motivo_anulacion` VARCHAR(400) AFTER `estado`,
  ADD COLUMN `anulado_en` DATETIME(6) AFTER `motivo_anulacion`,
  ADD COLUMN `anulado_por` BIGINT UNSIGNED AFTER `anulado_en`,
  ADD CONSTRAINT `fk_contratos_anulado_por_usuarios`
    FOREIGN KEY (`anulado_por`)
    REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `ck_contratos_tipo_contrato`
    CHECK (`tipo_contrato` IN (
      'PROMESA_COMPRAVENTA','COMPRAVENTA','CESION'
    )),
  ADD CONSTRAINT `ck_contratos_totales_derivados`
    CHECK (
      `valor_total` = `valor_lote` + `otros_conceptos` - `descuento`
      AND `saldo_financiado` = `valor_total` - `cuota_inicial`
      AND `cuota_inicial` <= `valor_total`
    ),
  ADD CONSTRAINT `ck_contratos_rango_fechas`
    CHECK (
      `fecha_finalizacion` IS NULL
      OR `fecha_finalizacion` >= `fecha_inicio`
    ),
  ADD KEY `idx_contratos_anulado_por` (`anulado_por`);

UPDATE `contratos`
SET `motivo_anulacion` = 'Registro anulado antes de V12',
    `anulado_en` = `actualizado_en`
WHERE `estado` = 'ANULADO'
  AND (`motivo_anulacion` IS NULL OR `anulado_en` IS NULL);

ALTER TABLE `contratos`
  ADD CONSTRAINT `ck_contratos_anulacion_trazable`
    CHECK (
      (`estado` = 'ANULADO'
       AND `motivo_anulacion` IS NOT NULL
       AND `anulado_en` IS NOT NULL)
      OR (`estado` <> 'ANULADO'
          AND `motivo_anulacion` IS NULL
          AND `anulado_en` IS NULL)
    );

-- Se conserva una única versión documental vigente por contrato.
UPDATE `contrato_versiones` cv
JOIN (
  SELECT `contrato_id`, MAX(`numero_version`) AS `ultima_version`
  FROM `contrato_versiones`
  GROUP BY `contrato_id`
) ult ON ult.`contrato_id` = cv.`contrato_id`
SET cv.`vigente` = CASE
  WHEN cv.`numero_version` = ult.`ultima_version` THEN 1
  ELSE 0
END;

ALTER TABLE `contrato_versiones`
  ADD COLUMN `vigente_actual` TINYINT
    GENERATED ALWAYS AS (
      CASE WHEN `vigente` = 1 THEN 1 ELSE NULL END
    ) STORED,
  ADD CONSTRAINT `uk_contrato_versiones_vigente_actual`
    UNIQUE (`contrato_id`, `vigente_actual`);

ALTER TABLE `contrato_firmantes`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0
    AFTER `archivo_firma_id`,
  ADD CONSTRAINT `ck_contrato_firmantes_orden`
    CHECK (`orden_firma` > 0),
  ADD CONSTRAINT `ck_contrato_firmantes_fecha_firma`
    CHECK (
      (`estado` = 'FIRMADA' AND `fecha_firma` IS NOT NULL)
      OR (`estado` <> 'FIRMADA')
    );

ALTER TABLE `firmas_contrato`
  ADD CONSTRAINT `ck_firmas_contrato_hash_sha256`
    CHECK (`hash_documento` REGEXP '^[0-9A-Fa-f]{64}$');
