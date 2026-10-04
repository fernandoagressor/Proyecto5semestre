-- NEXORA CORE
-- Corte 14: índices para bandejas operativas del portal y servicio al cliente.
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

CREATE INDEX `idx_solicitudes_pqrs_empresa_estado_creado`
  ON `solicitudes_pqrs` (`empresa_id`, `estado`, `creado_en`);

CREATE INDEX `idx_solicitudes_pqrs_empresa_tipo_creado`
  ON `solicitudes_pqrs` (`empresa_id`, `tipo`, `creado_en`);

CREATE INDEX `idx_citas_servicio_empresa_fechas_estado`
  ON `citas_servicio` (`empresa_id`, `fecha_inicio`, `fecha_fin`, `estado`);

CREATE INDEX `idx_solicitud_adjuntos_solicitud_creado`
  ON `solicitud_adjuntos` (`solicitud_id`, `creado_en`);
