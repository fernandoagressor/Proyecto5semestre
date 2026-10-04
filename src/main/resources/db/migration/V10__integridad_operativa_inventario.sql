-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V10: integridad operativa del módulo de proyectos e inventario
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- El indicador disponible debe representar exactamente el estado del lote.
UPDATE `lotes`
SET `disponible` = CASE WHEN `estado` = 'DISPONIBLE' THEN 1 ELSE 0 END;

ALTER TABLE `lotes`
  ADD CONSTRAINT `ck_lotes_estado_disponible`
  CHECK (
    (`estado` = 'DISPONIBLE' AND `disponible` = 1)
    OR
    (`estado` <> 'DISPONIBLE' AND `disponible` = 0)
  );

-- Solo puede existir un precio vigente por lote. Los históricos cerrados
-- conservan NULL y pueden repetirse sin violar el índice único.
ALTER TABLE `lote_precios_historial`
  ADD COLUMN `vigente_actual` TINYINT
    GENERATED ALWAYS AS (
      CASE WHEN `vigente_hasta` IS NULL THEN 1 ELSE NULL END
    ) STORED,
  ADD CONSTRAINT `uk_lote_precios_historial_precio_vigente`
    UNIQUE (`lote_id`, `vigente_actual`);

-- Compatibilidad para instalaciones creadas con el primer corte: todo
-- ADMINISTRADOR interno obtiene el alcance empresarial requerido por el API.
INSERT IGNORE INTO `usuario_alcances`
  (`usuario_id`, `empresa_id`, `proyecto_id`, `tipo_alcance`,
   `vigente_desde`, `creado_en`, `actualizado_en`)
SELECT DISTINCT
  u.id,
  u.empresa_id,
  NULL,
  'EMPRESA',
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6)
FROM `usuarios` u
JOIN `usuario_roles` ur ON ur.usuario_id = u.id
JOIN `roles` r ON r.id = ur.rol_id
WHERE u.empresa_id IS NOT NULL
  AND r.codigo = 'ADMINISTRADOR'
  AND r.activo = 1;
