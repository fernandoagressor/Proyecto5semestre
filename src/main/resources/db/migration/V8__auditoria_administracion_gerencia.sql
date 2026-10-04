-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V8: auditoria administracion gerencia
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Auditoría: Bitácora inmutable de accesos y operaciones sensibles.
CREATE TABLE `auditoria_eventos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `actor_id` BIGINT UNSIGNED,
  `modulo` VARCHAR(60) NOT NULL,
  `accion` VARCHAR(60) NOT NULL,
  `entidad_tipo` VARCHAR(60),
  `entidad_id` BIGINT UNSIGNED,
  `descripcion` VARCHAR(700) NOT NULL,
  `datos_anteriores_json` JSON,
  `datos_nuevos_json` JSON,
  `ip_origen` VARCHAR(64),
  `agente_usuario` VARCHAR(500),
  `correlation_id` CHAR(36) NOT NULL,
  `ocurrido_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_auditoria_eventos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_auditoria_eventos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_auditoria_eventos_actor_id_usuarios` FOREIGN KEY (`actor_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_auditoria_eventos_empresa_id_ocurrido_en` (`empresa_id`, `ocurrido_en`),
  KEY `idx_auditoria_eventos_actor_id_ocurrido_en` (`actor_id`, `ocurrido_en`),
  KEY `idx_auditoria_eventos_entidad_tipo_entidad_id` (`entidad_tipo`, `entidad_id`),
  KEY `idx_auditoria_eventos_modulo_accion_ocurrido_en` (`modulo`, `accion`, `ocurrido_en`),
  KEY `idx_auditoria_eventos_empresa_id` (`empresa_id`),
  KEY `idx_auditoria_eventos_proyecto_id` (`proyecto_id`),
  KEY `idx_auditoria_eventos_actor_id` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bitácora inmutable de accesos y operaciones sensibles.';

-- Auditoría: Alertas de accesos y acciones inusuales.
CREATE TABLE `alertas_anomalia` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `tipo` VARCHAR(60) NOT NULL,
  `severidad` VARCHAR(15) NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `evento_auditoria_id` BIGINT UNSIGNED,
  `descripcion` TEXT NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
  `asignado_a` BIGINT UNSIGNED,
  `resuelta_en` DATETIME(6),
  `resolucion` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_alertas_anomalia_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_alertas_anomalia_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_alertas_anomalia_evento_auditoria_id_auditoria_eventos` FOREIGN KEY (`evento_auditoria_id`) REFERENCES `auditoria_eventos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_alertas_anomalia_asignado_a_usuarios` FOREIGN KEY (`asignado_a`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_alertas_anomalia_1` CHECK (severidad IN ('BAJA','MEDIA','ALTA','CRITICA')),
  CONSTRAINT `ck_alertas_anomalia_2` CHECK (estado IN ('ABIERTA','EN_INVESTIGACION','RESUELTA','DESCARTADA')),
  KEY `idx_alertas_anomalia_estado_severidad_creado_en` (`estado`, `severidad`, `creado_en`),
  KEY `idx_alertas_anomalia_empresa_id` (`empresa_id`),
  KEY `idx_alertas_anomalia_usuario_id` (`usuario_id`),
  KEY `idx_alertas_anomalia_evento_auditoria_id` (`evento_auditoria_id`),
  KEY `idx_alertas_anomalia_asignado_a` (`asignado_a`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Alertas de accesos y acciones inusuales.';

-- Auditoría: Control de exportaciones de evidencia.
CREATE TABLE `exportaciones_auditoria` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `usuario_id` BIGINT UNSIGNED NOT NULL,
  `filtros_json` JSON NOT NULL,
  `formato` VARCHAR(10) NOT NULL,
  `archivo_id` BIGINT UNSIGNED,
  `motivo` VARCHAR(500) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `expira_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_exportaciones_auditoria_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_exportaciones_auditoria_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_exportaciones_auditoria_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_exportaciones_auditoria_1` CHECK (formato IN ('CSV','XLSX','PDF')),
  CONSTRAINT `ck_exportaciones_auditoria_2` CHECK (estado IN ('PENDIENTE','GENERADA','FALLIDA','EXPIRADA')),
  KEY `idx_exportaciones_auditoria_empresa_id` (`empresa_id`),
  KEY `idx_exportaciones_auditoria_usuario_id` (`usuario_id`),
  KEY `idx_exportaciones_auditoria_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Control de exportaciones de evidencia.';

-- Administración global: Parámetros globales protegidos.
CREATE TABLE `configuraciones_sistema` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `clave` VARCHAR(120) NOT NULL,
  `valor_json` JSON NOT NULL,
  `descripcion` VARCHAR(500),
  `sensible` TINYINT(1) NOT NULL DEFAULT 0,
  `actualizado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_configuraciones_sistema_clave` UNIQUE (`clave`),
  CONSTRAINT `fk_configuraciones_sistema_actualizado_por_usuarios` FOREIGN KEY (`actualizado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_configuraciones_sistema_actualizado_por` (`actualizado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Parámetros globales protegidos.';

-- Administración global: Configuración y salud de integraciones externas.
CREATE TABLE `integraciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `codigo` VARCHAR(60) NOT NULL,
  `tipo` VARCHAR(40) NOT NULL,
  `nombre` VARCHAR(140) NOT NULL,
  `configuracion_cifrada` LONGBLOB NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'INACTIVA',
  `ultimo_chequeo_en` DATETIME(6),
  `ultimo_error` VARCHAR(700),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_integraciones_ambito_empresa_id_codigo` UNIQUE (`ambito_empresa_id`, `codigo`),
  CONSTRAINT `fk_integraciones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_integraciones_1` CHECK (tipo IN ('PASARELA_PAGO','CORREO','SMS','FIRMA','ALMACENAMIENTO','OTRO')),
  CONSTRAINT `ck_integraciones_2` CHECK (estado IN ('ACTIVA','INACTIVA','ERROR')),
  KEY `idx_integraciones_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Configuración y salud de integraciones externas.';

-- Administración global: Registro de copias de seguridad y pruebas de restauración.
CREATE TABLE `respaldos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tipo` VARCHAR(20) NOT NULL,
  `ubicacion` VARCHAR(700) NOT NULL,
  `tamano_bytes` BIGINT UNSIGNED NOT NULL,
  `sha256` CHAR(64) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'EN_PROCESO',
  `iniciado_en` DATETIME(6) NOT NULL,
  `finalizado_en` DATETIME(6),
  `verificado_en` DATETIME(6),
  `detalle_error` TEXT,
  `solicitado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_respaldos_solicitado_por_usuarios` FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_respaldos_1` CHECK (tipo IN ('COMPLETO','INCREMENTAL','ARCHIVOS')),
  CONSTRAINT `ck_respaldos_2` CHECK (estado IN ('EN_PROCESO','COMPLETADO','FALLIDO','ELIMINADO')),
  KEY `idx_respaldos_solicitado_por` (`solicitado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Registro de copias de seguridad y pruebas de restauración.';

-- Reportes: Historial de reportes operativos, ejecutivos y financieros.
CREATE TABLE `reportes_generados` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `solicitado_por` BIGINT UNSIGNED,
  `tipo_reporte` VARCHAR(60) NOT NULL,
  `filtros_json` JSON NOT NULL,
  `formato` VARCHAR(10) NOT NULL,
  `archivo_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `generado_en` DATETIME(6),
  `expira_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_reportes_generados_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reportes_generados_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reportes_generados_solicitado_por_usuarios` FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reportes_generados_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_reportes_generados_1` CHECK (formato IN ('PDF','XLSX','CSV')),
  CONSTRAINT `ck_reportes_generados_2` CHECK (estado IN ('PENDIENTE','GENERADO','FALLIDO','EXPIRADO')),
  KEY `idx_reportes_generados_empresa_id` (`empresa_id`),
  KEY `idx_reportes_generados_proyecto_id` (`proyecto_id`),
  KEY `idx_reportes_generados_solicitado_por` (`solicitado_por`),
  KEY `idx_reportes_generados_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial de reportes operativos, ejecutivos y financieros.';

-- Gerencia financiera: Catálogo de costos para rentabilidad por proyecto.
CREATE TABLE `categorias_costo` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(40) NOT NULL,
  `nombre` VARCHAR(140) NOT NULL,
  `tipo` VARCHAR(20) NOT NULL,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_categorias_costo_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_categorias_costo_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_categorias_costo_1` CHECK (tipo IN ('DIRECTO','INDIRECTO','ADMINISTRATIVO')),
  KEY `idx_categorias_costo_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Catálogo de costos para rentabilidad por proyecto.';

-- Gerencia financiera: Presupuesto aprobado por proyecto, categoría y periodo.
CREATE TABLE `presupuestos_proyecto` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `categoria_costo_id` BIGINT UNSIGNED NOT NULL,
  `periodo_desde` DATE NOT NULL,
  `periodo_hasta` DATE NOT NULL,
  `valor_presupuestado` DECIMAL(19,2) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `aprobado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_presupuestos_proyecto_proyecto_id_categoria_cost_7b596043` UNIQUE (`proyecto_id`, `categoria_costo_id`, `periodo_desde`, `periodo_hasta`),
  CONSTRAINT `fk_presupuestos_proyecto_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_presupuestos_proyecto_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_presupuestos_proyecto_categoria_costo_id_categorias_costo` FOREIGN KEY (`categoria_costo_id`) REFERENCES `categorias_costo` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_presupuestos_proyecto_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_presupuestos_proyecto_1` CHECK (periodo_hasta >= periodo_desde),
  CONSTRAINT `ck_presupuestos_proyecto_2` CHECK (valor_presupuestado >= 0),
  CONSTRAINT `ck_presupuestos_proyecto_3` CHECK (estado IN ('BORRADOR','APROBADO','CERRADO','ANULADO')),
  KEY `idx_presupuestos_proyecto_empresa_id` (`empresa_id`),
  KEY `idx_presupuestos_proyecto_proyecto_id` (`proyecto_id`),
  KEY `idx_presupuestos_proyecto_categoria_costo_id` (`categoria_costo_id`),
  KEY `idx_presupuestos_proyecto_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Presupuesto aprobado por proyecto, categoría y periodo.';

-- Gerencia financiera: Costos reales para calcular utilidad y margen.
CREATE TABLE `costos_proyecto` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `categoria_costo_id` BIGINT UNSIGNED NOT NULL,
  `fecha_costo` DATE NOT NULL,
  `concepto` VARCHAR(220) NOT NULL,
  `proveedor` VARCHAR(180),
  `referencia` VARCHAR(100),
  `valor` DECIMAL(19,2) NOT NULL,
  `comprobante_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
  `registrado_por` BIGINT UNSIGNED,
  `aprobado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_costos_proyecto_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_costos_proyecto_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_costos_proyecto_categoria_costo_id_categorias_costo` FOREIGN KEY (`categoria_costo_id`) REFERENCES `categorias_costo` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_costos_proyecto_comprobante_id_archivos` FOREIGN KEY (`comprobante_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_costos_proyecto_registrado_por_usuarios` FOREIGN KEY (`registrado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_costos_proyecto_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_costos_proyecto_1` CHECK (valor > 0),
  CONSTRAINT `ck_costos_proyecto_2` CHECK (estado IN ('REGISTRADO','APROBADO','PAGADO','ANULADO')),
  KEY `idx_costos_proyecto_proyecto_id_fecha_costo_estado` (`proyecto_id`, `fecha_costo`, `estado`),
  KEY `idx_costos_proyecto_empresa_id` (`empresa_id`),
  KEY `idx_costos_proyecto_proyecto_id` (`proyecto_id`),
  KEY `idx_costos_proyecto_categoria_costo_id` (`categoria_costo_id`),
  KEY `idx_costos_proyecto_comprobante_id` (`comprobante_id`),
  KEY `idx_costos_proyecto_registrado_por` (`registrado_por`),
  KEY `idx_costos_proyecto_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Costos reales para calcular utilidad y margen.';
