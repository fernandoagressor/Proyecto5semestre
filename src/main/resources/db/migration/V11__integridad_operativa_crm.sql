-- Plataforma de Gestión Inmobiliaria
-- Migración V11: integridad operativa y concurrencia del CRM comercial.
-- No modifica los datos ni la estructura de V1-V10.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

ALTER TABLE `prospectos`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `observaciones`,
  ADD CONSTRAINT `ck_prospectos_contacto`
    CHECK (`telefono` IS NOT NULL OR `correo` IS NOT NULL),
  ADD CONSTRAINT `ck_prospectos_fuente`
    CHECK (`fuente` IN (
      'WEB','REDES_SOCIALES','REFERIDO','PUBLICIDAD',
      'FERIA','LLAMADA','VISITA','OTRO'
    ));

ALTER TABLE `oportunidades`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `fecha_cierre`,
  ADD CONSTRAINT `ck_oportunidades_etapa_actual`
    CHECK (`etapa_actual` IN (
      'PROSPECTO_RECIBIDO','CONTACTO_INICIAL','CALIFICACION',
      'PRESENTACION_PROYECTO','VISITA','PROPUESTA',
      'NEGOCIACION','CIERRE'
    )),
  ADD CONSTRAINT `ck_oportunidades_cierre`
    CHECK (
      (`estado` = 'ABIERTA' AND `fecha_cierre` IS NULL)
      OR (`estado` <> 'ABIERTA' AND `fecha_cierre` IS NOT NULL)
    );

ALTER TABLE `oportunidad_lotes`
  ADD CONSTRAINT `ck_oportunidad_lotes_prioridad`
    CHECK (`prioridad` > 0),
  ADD KEY `idx_oportunidad_lotes_seleccion`
    (`oportunidad_id`, `seleccionado`, `prioridad`);

ALTER TABLE `citas_comerciales`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `resultado`;

ALTER TABLE `simulaciones`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `estado`,
  ADD CONSTRAINT `ck_simulaciones_periodicidad`
    CHECK (`periodicidad` IN (
      'MENSUAL','BIMESTRAL','TRIMESTRAL','SEMESTRAL','ANUAL'
    )),
  ADD CONSTRAINT `ck_simulaciones_saldo`
    CHECK (
      `descuento` + `cuota_inicial` <= `valor_lote`
      AND `saldo_financiar` =
          `valor_lote` - `descuento` - `cuota_inicial`
    );

ALTER TABLE `simulacion_cuotas`
  ADD CONSTRAINT `ck_simulacion_cuotas_componentes`
    CHECK (
      `capital` >= 0
      AND `interes` >= 0
      AND `otros` >= 0
      AND `saldo_proyectado` >= 0
      AND `valor_total` = `capital` + `interes` + `otros`
    );
