-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V6: comisiones tareas documentos
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Comisiones y metas: Reglas versionadas de causación de comisiones.
CREATE TABLE `reglas_comision` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `nombre` VARCHAR(140) NOT NULL,
  `tipo` VARCHAR(20) NOT NULL,
  `valor` DECIMAL(19,6) NOT NULL,
  `condicion_causacion` VARCHAR(40) NOT NULL,
  `vigente_desde` DATE NOT NULL,
  `vigente_hasta` DATE,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_reglas_comision_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_reglas_comision_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_reglas_comision_1` CHECK (tipo IN ('PORCENTAJE','VALOR_FIJO')),
  CONSTRAINT `ck_reglas_comision_2` CHECK (valor >= 0),
  KEY `idx_reglas_comision_empresa_id` (`empresa_id`),
  KEY `idx_reglas_comision_proyecto_id` (`proyecto_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Reglas versionadas de causación de comisiones.';

-- Comisiones y metas: Comisión atribuida a una venta y asesor.
CREATE TABLE `comisiones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `regla_comision_id` BIGINT UNSIGNED NOT NULL,
  `base_calculo` DECIMAL(19,2) NOT NULL,
  `porcentaje` DECIMAL(9,6),
  `valor_comision` DECIMAL(19,2) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PROYECTADA',
  `fecha_causacion` DATE,
  `fecha_pago` DATE,
  `aprobado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_comisiones_contrato_id_asesor_id_regla_comision_id` UNIQUE (`contrato_id`, `asesor_id`, `regla_comision_id`),
  CONSTRAINT `fk_comisiones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comisiones_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comisiones_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_comisiones_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_comisiones_regla_comision_id_reglas_comision` FOREIGN KEY (`regla_comision_id`) REFERENCES `reglas_comision` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_comisiones_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_comisiones_1` CHECK (estado IN ('PROYECTADA','CAUSADA','APROBADA','PAGADA','ANULADA')),
  KEY `idx_comisiones_empresa_id` (`empresa_id`),
  KEY `idx_comisiones_proyecto_id` (`proyecto_id`),
  KEY `idx_comisiones_asesor_id` (`asesor_id`),
  KEY `idx_comisiones_contrato_id` (`contrato_id`),
  KEY `idx_comisiones_regla_comision_id` (`regla_comision_id`),
  KEY `idx_comisiones_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comisión atribuida a una venta y asesor.';

-- Comisiones y metas: Metas por empresa, proyecto, equipo o asesor.
CREATE TABLE `metas_comerciales` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `asesor_id` BIGINT UNSIGNED,
  `ambito_proyecto_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(proyecto_id, 0)) STORED,
  `ambito_asesor_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(asesor_id, 0)) STORED,
  `periodo` VARCHAR(7) NOT NULL,
  `valor_meta` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `cantidad_meta` INT UNSIGNED NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `aprobado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_metas_comerciales_empresa_id_ambito_proyecto_id__427ad54d` UNIQUE (`empresa_id`, `ambito_proyecto_id`, `ambito_asesor_id`, `periodo`),
  CONSTRAINT `fk_metas_comerciales_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_metas_comerciales_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_metas_comerciales_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_metas_comerciales_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_metas_comerciales_1` CHECK (periodo REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  CONSTRAINT `ck_metas_comerciales_2` CHECK (estado IN ('BORRADOR','APROBADA','CERRADA')),
  KEY `idx_metas_comerciales_empresa_id` (`empresa_id`),
  KEY `idx_metas_comerciales_proyecto_id` (`proyecto_id`),
  KEY `idx_metas_comerciales_asesor_id` (`asesor_id`),
  KEY `idx_metas_comerciales_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metas por empresa, proyecto, equipo o asesor.';

-- Gobierno operativo: Flujo genérico de aprobación para descuentos, ajustes y excepciones.
CREATE TABLE `aprobaciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(40) NOT NULL,
  `entidad_tipo` VARCHAR(50) NOT NULL,
  `entidad_id` BIGINT UNSIGNED NOT NULL,
  `solicitado_por` BIGINT UNSIGNED NOT NULL,
  `asignado_a` BIGINT UNSIGNED,
  `motivo` TEXT NOT NULL,
  `datos_json` JSON NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `decision_en` DATETIME(6),
  `comentario_decision` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_aprobaciones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_aprobaciones_solicitado_por_usuarios` FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_aprobaciones_asignado_a_usuarios` FOREIGN KEY (`asignado_a`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_aprobaciones_1` CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA','CANCELADA')),
  KEY `idx_aprobaciones_asignado_a_estado` (`asignado_a`, `estado`),
  KEY `idx_aprobaciones_entidad_tipo_entidad_id` (`entidad_tipo`, `entidad_id`),
  KEY `idx_aprobaciones_empresa_id` (`empresa_id`),
  KEY `idx_aprobaciones_solicitado_por` (`solicitado_por`),
  KEY `idx_aprobaciones_asignado_a` (`asignado_a`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Flujo genérico de aprobación para descuentos, ajustes y excepciones.';

-- Tareas: Tareas manuales o automáticas ligadas a operaciones reales.
CREATE TABLE `tareas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `asignado_a` BIGINT UNSIGNED NOT NULL,
  `creado_por` BIGINT UNSIGNED,
  `titulo` VARCHAR(180) NOT NULL,
  `descripcion` TEXT,
  `tipo` VARCHAR(40) NOT NULL,
  `prioridad` VARCHAR(15) NOT NULL DEFAULT 'MEDIA',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `fecha_vencimiento` DATETIME(6),
  `entidad_tipo` VARCHAR(50),
  `entidad_id` BIGINT UNSIGNED,
  `regla_origen` VARCHAR(100),
  `completada_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_tareas_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tareas_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_tareas_asignado_a_usuarios` FOREIGN KEY (`asignado_a`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tareas_creado_por_usuarios` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_tareas_1` CHECK (prioridad IN ('BAJA','MEDIA','ALTA','CRITICA')),
  CONSTRAINT `ck_tareas_2` CHECK (estado IN ('PENDIENTE','EN_PROCESO','COMPLETADA','VENCIDA','CANCELADA')),
  KEY `idx_tareas_asignado_a_estado_fecha_vencimiento` (`asignado_a`, `estado`, `fecha_vencimiento`),
  KEY `idx_tareas_entidad_tipo_entidad_id` (`entidad_tipo`, `entidad_id`),
  KEY `idx_tareas_empresa_id` (`empresa_id`),
  KEY `idx_tareas_proyecto_id` (`proyecto_id`),
  KEY `idx_tareas_asignado_a` (`asignado_a`),
  KEY `idx_tareas_creado_por` (`creado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tareas manuales o automáticas ligadas a operaciones reales.';

-- Tareas: Historial de estado, asignación y evidencia de tareas.
CREATE TABLE `tarea_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tarea_id` BIGINT UNSIGNED NOT NULL,
  `estado_anterior` VARCHAR(20),
  `estado_nuevo` VARCHAR(20) NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `comentario` VARCHAR(500),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_tarea_historial_tarea_id_tareas` FOREIGN KEY (`tarea_id`) REFERENCES `tareas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tarea_historial_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_tarea_historial_tarea_id_creado_en` (`tarea_id`, `creado_en`),
  KEY `idx_tarea_historial_tarea_id` (`tarea_id`),
  KEY `idx_tarea_historial_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial de estado, asignación y evidencia de tareas.';

-- Documentos: Plantillas versionadas para contratos, recibos y certificados.
CREATE TABLE `plantillas_documento` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `codigo` VARCHAR(60) NOT NULL,
  `nombre` VARCHAR(160) NOT NULL,
  `tipo_documento` VARCHAR(50) NOT NULL,
  `contenido_plantilla` LONGTEXT NOT NULL,
  `version` INT UNSIGNED NOT NULL DEFAULT 1,
  `variables_json` JSON NOT NULL,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_plantillas_documento_ambito_empresa_id_codigo_version` UNIQUE (`ambito_empresa_id`, `codigo`, `version`),
  CONSTRAINT `fk_plantillas_documento_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plantillas_documento_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  KEY `idx_plantillas_documento_empresa_id` (`empresa_id`),
  KEY `idx_plantillas_documento_proyecto_id` (`proyecto_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Plantillas versionadas para contratos, recibos y certificados.';

-- Documentos: Documento lógico visible en módulos y portal.
CREATE TABLE `documentos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `codigo` VARCHAR(60) NOT NULL,
  `tipo_documento` VARCHAR(50) NOT NULL,
  `nombre` VARCHAR(200) NOT NULL,
  `cliente_id` BIGINT UNSIGNED,
  `entidad_tipo` VARCHAR(50) NOT NULL,
  `entidad_id` BIGINT UNSIGNED NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `visible_portal` TINYINT(1) NOT NULL DEFAULT 0,
  `creado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_documentos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_documentos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_documentos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_documentos_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_documentos_creado_por_usuarios` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_documentos_1` CHECK (estado IN ('BORRADOR','VIGENTE','ANULADO','ARCHIVADO')),
  KEY `idx_documentos_entidad_tipo_entidad_id` (`entidad_tipo`, `entidad_id`),
  KEY `idx_documentos_cliente_id_visible_portal` (`cliente_id`, `visible_portal`),
  KEY `idx_documentos_empresa_id` (`empresa_id`),
  KEY `idx_documentos_proyecto_id` (`proyecto_id`),
  KEY `idx_documentos_cliente_id` (`cliente_id`),
  KEY `idx_documentos_creado_por` (`creado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Documento lógico visible en módulos y portal.';

-- Documentos: Archivos y metadatos de cada versión documental.
CREATE TABLE `documento_versiones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `documento_id` BIGINT UNSIGNED NOT NULL,
  `numero_version` INT UNSIGNED NOT NULL,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `plantilla_id` BIGINT UNSIGNED,
  `hash_contenido` CHAR(64) NOT NULL,
  `motivo` VARCHAR(400) NOT NULL,
  `creado_por` BIGINT UNSIGNED,
  `vigente` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_documento_versiones_documento_id_numero_version` UNIQUE (`documento_id`, `numero_version`),
  CONSTRAINT `fk_documento_versiones_documento_id_documentos` FOREIGN KEY (`documento_id`) REFERENCES `documentos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_documento_versiones_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_documento_versiones_plantilla_id_plantillas_documento` FOREIGN KEY (`plantilla_id`) REFERENCES `plantillas_documento` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_documento_versiones_creado_por_usuarios` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_documento_versiones_documento_id` (`documento_id`),
  KEY `idx_documento_versiones_archivo_id` (`archivo_id`),
  KEY `idx_documento_versiones_plantilla_id` (`plantilla_id`),
  KEY `idx_documento_versiones_creado_por` (`creado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Archivos y metadatos de cada versión documental.';
