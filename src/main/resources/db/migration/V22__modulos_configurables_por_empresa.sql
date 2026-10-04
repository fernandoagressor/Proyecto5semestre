-- Módulos funcionales configurables por empresa.
-- Las empresas existentes conservan todas las capacidades activas.
CREATE TABLE `empresa_modulos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(60) NOT NULL,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_empresa_modulos_empresa_codigo`
    UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_empresa_modulos_empresa`
    FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  KEY `idx_empresa_modulos_empresa_activo` (`empresa_id`, `activo`)
) ENGINE=InnoDB
  COMMENT='Módulos funcionales habilitados para cada empresa.';

INSERT INTO `empresa_modulos` (`empresa_id`, `codigo`, `activo`)
SELECT e.id, modulos.codigo, 1
FROM `empresas` e
CROSS JOIN (
  SELECT 'ADMINISTRACION_SEGURIDAD' AS codigo
  UNION ALL SELECT 'PROYECTOS_INVENTARIO'
  UNION ALL SELECT 'CLIENTES_VENTAS'
  UNION ALL SELECT 'RESERVAS_CONTRATOS'
  UNION ALL SELECT 'CARTERA_PAGOS'
  UNION ALL SELECT 'PORTAL_SERVICIO'
  UNION ALL SELECT 'GERENCIA_REPORTES'
) modulos;
