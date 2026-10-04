-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V16: consola de superadministración multiempresa
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- La sesión conserva el espacio de trabajo seleccionado por SUPER_ADMIN.
ALTER TABLE `sesiones_usuario`
  ADD COLUMN `empresa_contexto_id` BIGINT UNSIGNED NULL AFTER `usuario_id`,
  ADD CONSTRAINT `fk_sesiones_usuario_empresa_contexto_id_empresas`
    FOREIGN KEY (`empresa_contexto_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  ADD KEY `idx_sesiones_usuario_empresa_contexto_id` (`empresa_contexto_id`);

UPDATE `sesiones_usuario` s
JOIN `usuarios` u ON u.id = s.usuario_id
SET s.empresa_contexto_id = u.empresa_id
WHERE s.empresa_contexto_id IS NULL;

-- Control optimista para evitar sobrescrituras administrativas concurrentes.
ALTER TABLE `empresas`
  ADD COLUMN `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `limite_usuarios`;

ALTER TABLE `usuarios`
  ADD COLUMN `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `bloqueado_hasta`;

-- Capacidades sensibles exclusivas de gobierno de plataforma.
INSERT INTO `permisos`
  (`codigo`, `modulo`, `nombre`, `descripcion`, `sensible`, `activo`)
VALUES
  ('EMPRESAS_ACTIVAR', 'Empresas y seguridad', 'Activar empresas',
   'Permite reactivar una empresa suspendida o inactiva.', 1, 1),
  ('EMPRESAS_SUSPENDER', 'Empresas y seguridad', 'Suspender empresas',
   'Permite suspender temporalmente una empresa y su operación.', 1, 1),
  ('USUARIOS_CREDENCIALES_REINICIAR', 'Empresas y seguridad',
   'Reiniciar credenciales',
   'Permite establecer una contraseña temporal y revocar sesiones activas.', 1, 1),
  ('CONTEXTO_EMPRESA_CAMBIAR', 'Empresas y seguridad',
   'Cambiar espacio de trabajo',
   'Permite a SUPER_ADMIN operar dentro del contexto de otra empresa.', 1, 1)
ON DUPLICATE KEY UPDATE
  `nombre` = VALUES(`nombre`),
  `descripcion` = VALUES(`descripcion`),
  `sensible` = VALUES(`sensible`),
  `activo` = 1;

INSERT INTO `rol_permisos`
  (`rol_id`, `permiso_id`, `concedido`, `creado_en`, `actualizado_en`)
SELECT r.id, p.id, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `roles` r
JOIN `permisos` p
  ON p.codigo IN (
    'EMPRESAS_ACTIVAR',
    'EMPRESAS_SUSPENDER',
    'USUARIOS_CREDENCIALES_REINICIAR',
    'CONTEXTO_EMPRESA_CAMBIAR'
  )
WHERE r.codigo = 'SUPER_ADMIN'
  AND r.empresa_id IS NULL
ON DUPLICATE KEY UPDATE
  `concedido` = 1,
  `actualizado_en` = CURRENT_TIMESTAMP(6);

-- Todo SUPER_ADMIN existente recibe alcance empresarial en las empresas actuales.
INSERT INTO `usuario_alcances`
  (`usuario_id`, `empresa_id`, `proyecto_id`, `tipo_alcance`, `asignado_por`,
   `vigente_desde`, `creado_en`, `actualizado_en`)
SELECT u.id, e.id, NULL, 'EMPRESA', u.id,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `usuarios` u
JOIN `usuario_roles` ur ON ur.usuario_id = u.id
JOIN `roles` r ON r.id = ur.rol_id
CROSS JOIN `empresas` e
WHERE r.codigo = 'SUPER_ADMIN'
  AND r.empresa_id IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `usuario_alcances` ua
    WHERE ua.usuario_id = u.id
      AND ua.empresa_id = e.id
      AND ua.proyecto_id IS NULL
      AND ua.tipo_alcance = 'EMPRESA'
  );

