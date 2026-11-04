-- Avances de obra: cada proyecto registra cuánto ha avanzado la construcción, con fecha y descripción.
-- El último avance actualiza proyectos.porcentaje_avance, que ya muestran el catálogo y los paneles.
SET NAMES utf8mb4;

CREATE TABLE `avances_obra` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `fecha` DATE NOT NULL,
  `porcentaje` DECIMAL(5,2) NOT NULL,
  `descripcion` VARCHAR(1000) NOT NULL,
  `registrado_por` BIGINT UNSIGNED NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_avances_obra_proyecto_fecha` (`proyecto_id`, `fecha`),
  KEY `idx_avances_obra_empresa` (`empresa_id`),
  CONSTRAINT `fk_avances_obra_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_avances_obra_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_avances_obra_porcentaje` CHECK (`porcentaje` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Avances de obra reportados por proyecto.';

INSERT INTO `permisos` (`codigo`,`modulo`,`nombre`,`descripcion`,`sensible`,`activo`) VALUES
('AVANCES_OBRA_VER','Proyectos e inventario','Avances de obra Ver','Consultar el historial de avances de obra de un proyecto.',0,1),
('AVANCES_OBRA_REGISTRAR','Proyectos e inventario','Avances de obra Registrar','Registrar un avance de obra y actualizar el porcentaje del proyecto.',1,1);

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo IN ('AVANCES_OBRA_VER','AVANCES_OBRA_REGISTRAR')
WHERE r.codigo IN ('SUPER_ADMIN','ADMINISTRADOR') AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo = 'AVANCES_OBRA_VER'
WHERE r.codigo IN ('GERENCIA','AUDITOR') AND r.empresa_id IS NULL;
