-- Catálogo público de proyectos y galería de fotos (rediseño, fase 4).
-- La empresa decide si publica su catálogo; solo se muestran proyectos ACTIVOS y lotes DISPONIBLES.
ALTER TABLE `empresas`
  ADD COLUMN `catalogo_publico` TINYINT(1) NOT NULL DEFAULT 0 AFTER `logo_url`;

CREATE TABLE `proyecto_imagenes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `url` VARCHAR(500) NOT NULL,
  `titulo` VARCHAR(160),
  `orden` INT UNSIGNED NOT NULL DEFAULT 0,
  `creado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_proyecto_imagenes_proyecto_orden` (`proyecto_id`, `orden`),
  CONSTRAINT `fk_proyecto_imagenes_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_proyecto_imagenes_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_proyecto_imagenes_url_https` CHECK (`url` LIKE 'https://%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Fotos de un proyecto para su ficha y el catálogo público.';
