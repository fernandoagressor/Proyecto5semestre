-- Desistimiento de una compra (rediseño, fase 4c): solicitud con liquidación, aprobación que anula
-- el contrato, cancela las cuotas abiertas, libera el lote y anula comisiones no pagadas, y acta PDF.
SET NAMES utf8mb4;

CREATE TABLE `desistimientos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(40) NOT NULL,
  `origen` VARCHAR(20) NOT NULL,
  `motivo` VARCHAR(1000) NOT NULL,
  `total_pagado` DECIMAL(19,2) NOT NULL,
  `porcentaje_penalidad` DECIMAL(5,2) NOT NULL,
  `valor_penalidad` DECIMAL(19,2) NOT NULL,
  `otros_descuentos` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `valor_devolver` DECIMAL(19,2) NOT NULL,
  `fecha_devolucion` DATE,
  `observaciones` VARCHAR(2000),
  `estado` VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',
  `solicitado_por` BIGINT UNSIGNED NOT NULL,
  `revisado_por` BIGINT UNSIGNED,
  `revisado_en` DATETIME(6),
  `comentario_revision` VARCHAR(1000),
  `cuotas_canceladas` INT UNSIGNED NOT NULL DEFAULT 0,
  `comisiones_anuladas` INT UNSIGNED NOT NULL DEFAULT 0,
  `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `clave_contrato_abierta` BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN `estado` = 'SOLICITADO' THEN `contrato_id` ELSE NULL END) STORED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_desistimientos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `uk_desistimientos_contrato_abierto` UNIQUE (`clave_contrato_abierta`),
  KEY `idx_desistimientos_empresa_estado` (`empresa_id`, `estado`),
  KEY `idx_desistimientos_contrato` (`contrato_id`),
  CONSTRAINT `fk_desistimientos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_desistimientos_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_desistimientos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_desistimientos_estado` CHECK (`estado` IN ('SOLICITADO','APROBADO','RECHAZADO')),
  CONSTRAINT `ck_desistimientos_origen` CHECK (`origen` IN ('CLIENTE','EMPRESA')),
  CONSTRAINT `ck_desistimientos_valores` CHECK (`total_pagado` >= 0 AND `porcentaje_penalidad` BETWEEN 0 AND 100
    AND `valor_penalidad` >= 0 AND `otros_descuentos` >= 0 AND `valor_devolver` >= 0),
  CONSTRAINT `ck_desistimientos_revision` CHECK (`estado` = 'SOLICITADO' OR (`revisado_por` IS NOT NULL AND `revisado_en` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Desistimientos de compra con su liquidación y aprobación.';

INSERT INTO `permisos` (`codigo`,`modulo`,`nombre`,`descripcion`,`sensible`,`activo`) VALUES
('DESISTIMIENTOS_VER','Ventas','Desistimientos Ver','Consultar desistimientos, su liquidación y el acta.',0,1),
('DESISTIMIENTOS_SOLICITAR','Ventas','Desistimientos Solicitar','Registrar un desistimiento con su liquidación.',1,1),
('DESISTIMIENTOS_APROBAR','Ventas','Desistimientos Aprobar','Aprobar o rechazar: anula el contrato, cancela cuotas y libera el lote.',1,1);

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo IN ('DESISTIMIENTOS_VER','DESISTIMIENTOS_SOLICITAR','DESISTIMIENTOS_APROBAR')
WHERE r.codigo IN ('SUPER_ADMIN','ADMINISTRADOR') AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo IN ('DESISTIMIENTOS_VER','DESISTIMIENTOS_APROBAR')
WHERE r.codigo = 'GERENCIA' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo IN ('DESISTIMIENTOS_VER','DESISTIMIENTOS_SOLICITAR')
WHERE r.codigo = 'CARTERA' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p ON p.codigo = 'DESISTIMIENTOS_VER'
WHERE r.codigo = 'AUDITOR' AND r.empresa_id IS NULL;
