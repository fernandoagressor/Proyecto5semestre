-- NEXORA CORE
-- Migración V17: seguridad de credenciales y cierre de permisos predeterminados
-- Mantiene intactas las migraciones V1-V16.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Permite invalidar inmediatamente los JWT emitidos antes de un cambio o
-- reinicio de contraseña. El valor se incrementa con cada actualización.
ALTER TABLE `usuarios`
  ADD COLUMN `token_version` BIGINT UNSIGNED NOT NULL DEFAULT 0
  AFTER `requiere_cambio_password`;

-- ADMINISTRADOR opera con el dashboard gerencial dentro de su empresa.
-- AUDITOR obtiene el mismo panel en modo lectura según sus permisos funcionales.
INSERT INTO `rol_permisos`
  (`rol_id`, `permiso_id`, `concedido`, `creado_en`, `actualizado_en`)
SELECT r.id, p.id, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `roles` r
JOIN `permisos` p ON p.codigo = 'DASHBOARD_GERENCIAL_VER'
WHERE r.empresa_id IS NULL
  AND r.codigo IN ('ADMINISTRADOR', 'AUDITOR')
ON DUPLICATE KEY UPDATE
  `concedido` = 1,
  `actualizado_en` = CURRENT_TIMESTAMP(6);

-- Todo SUPER_ADMIN global conserva el catálogo completo, incluidos permisos
-- agregados por migraciones posteriores a los datos iniciales.
INSERT INTO `rol_permisos`
  (`rol_id`, `permiso_id`, `concedido`, `creado_en`, `actualizado_en`)
SELECT r.id, p.id, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `roles` r
CROSS JOIN `permisos` p
WHERE r.codigo = 'SUPER_ADMIN'
  AND r.empresa_id IS NULL
  AND r.activo = 1
  AND p.activo = 1
ON DUPLICATE KEY UPDATE
  `concedido` = 1,
  `actualizado_en` = CURRENT_TIMESTAMP(6);
