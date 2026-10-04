-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V14: integridad portal y servicio al cliente
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Control optimista para evitar que dos agentes sobrescriban la misma atención.
ALTER TABLE `solicitudes_pqrs`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `citas_servicio`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `comunicaciones_cliente`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `articulos_conocimiento`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `tickets_soporte`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `notificaciones`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `envios_notificacion`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

-- Solicitudes de modificación de datos personales con aprobación independiente.
CREATE TABLE `solicitudes_cambio_datos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `solicitado_por` BIGINT UNSIGNED NOT NULL,
  `campo` VARCHAR(60) NOT NULL,
  `valor_anterior` VARCHAR(500),
  `valor_solicitado` VARCHAR(500) NOT NULL,
  `motivo` VARCHAR(500) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `revisado_por` BIGINT UNSIGNED,
  `revisado_en` DATETIME(6),
  `comentario_revision` VARCHAR(500),
  `version` BIGINT NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_solicitudes_cambio_datos_empresa_id_empresas`
    FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitudes_cambio_datos_cliente_id_clientes`
    FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitudes_cambio_datos_solicitado_por_usuarios`
    FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_solicitudes_cambio_datos_revisado_por_usuarios`
    FOREIGN KEY (`revisado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_solicitudes_cambio_datos_campo`
    CHECK (`campo` IN ('CORREO','TELEFONO','DIRECCION','NOMBRES','APELLIDOS','DOCUMENTO')),
  CONSTRAINT `ck_solicitudes_cambio_datos_estado`
    CHECK (`estado` IN ('PENDIENTE','APROBADA','RECHAZADA','CANCELADA')),
  KEY `idx_solicitudes_cambio_datos_empresa_estado`
    (`empresa_id`, `estado`, `creado_en`),
  KEY `idx_solicitudes_cambio_datos_cliente_estado`
    (`cliente_id`, `estado`, `creado_en`),
  KEY `idx_solicitudes_cambio_datos_solicitado_por` (`solicitado_por`),
  KEY `idx_solicitudes_cambio_datos_revisado_por` (`revisado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Solicitudes trazables de cambio de datos del cliente.';

-- Índices de operación para bandejas, agenda y entregas asíncronas.
CREATE INDEX `idx_citas_servicio_responsable_fechas_estado`
  ON `citas_servicio` (`responsable_id`, `fecha_inicio`, `fecha_fin`, `estado`);

CREATE INDEX `idx_comunicaciones_cliente_estado_programada`
  ON `comunicaciones_cliente` (`estado`, `programada_para`);

CREATE INDEX `idx_envios_notificacion_estado_intentos`
  ON `envios_notificacion` (`estado`, `intentos`, `creado_en`);

CREATE INDEX `idx_articulos_conocimiento_portal_estado`
  ON `articulos_conocimiento`
  (`empresa_id`, `visible_clientes`, `estado`, `publicado_en`);

-- El cliente debe poder continuar la conversación de sus propios tickets.
INSERT INTO `rol_permisos` (`rol_id`, `permiso_id`, `concedido`)
SELECT r.id, p.id, 1
FROM `roles` r
JOIN `permisos` p ON p.codigo = 'SOPORTE_RESPONDER'
WHERE r.codigo = 'CLIENTE' AND r.empresa_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM `rol_permisos` rp
    WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
