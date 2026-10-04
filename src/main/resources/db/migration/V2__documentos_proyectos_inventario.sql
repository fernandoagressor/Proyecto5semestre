-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V2: documentos proyectos inventario
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Documentos: Metadatos de archivos almacenados fuera de MySQL.
CREATE TABLE `archivos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `subido_por` BIGINT UNSIGNED,
  `nombre_original` VARCHAR(255) NOT NULL,
  `nombre_almacenado` VARCHAR(255) NOT NULL,
  `ubicacion` VARCHAR(700) NOT NULL,
  `mime_type` VARCHAR(120) NOT NULL,
  `tamano_bytes` BIGINT UNSIGNED NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `clasificacion` VARCHAR(20) NOT NULL DEFAULT 'INTERNO',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_archivos_sha256_ubicacion` UNIQUE (`sha256`, `ubicacion`),
  CONSTRAINT `fk_archivos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_archivos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_archivos_subido_por_usuarios` FOREIGN KEY (`subido_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_archivos_1` CHECK (clasificacion IN ('PUBLICO','INTERNO','CONFIDENCIAL','RESTRINGIDO')),
  CONSTRAINT `ck_archivos_2` CHECK (estado IN ('ACTIVO','CUARENTENA','ELIMINADO')),
  KEY `idx_archivos_empresa_id` (`empresa_id`),
  KEY `idx_archivos_proyecto_id` (`proyecto_id`),
  KEY `idx_archivos_subido_por` (`subido_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metadatos de archivos almacenados fuera de MySQL.';

-- Núcleo multiempresa: Parámetros particulares de cada empresa.
CREATE TABLE `configuraciones_empresa` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `clave` VARCHAR(120) NOT NULL,
  `valor_json` JSON NOT NULL,
  `descripcion` VARCHAR(500),
  `sensible` TINYINT(1) NOT NULL DEFAULT 0,
  `actualizado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_configuraciones_empresa_empresa_id_clave` UNIQUE (`empresa_id`, `clave`),
  CONSTRAINT `fk_configuraciones_empresa_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_configuraciones_empresa_actualizado_por_usuarios` FOREIGN KEY (`actualizado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_configuraciones_empresa_empresa_id` (`empresa_id`),
  KEY `idx_configuraciones_empresa_actualizado_por` (`actualizado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Parámetros particulares de cada empresa.';

-- Proyectos: Avances generales y por componente visibles en el portal.
CREATE TABLE `proyecto_avances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `componente` VARCHAR(100) NOT NULL,
  `porcentaje` DECIMAL(5,2) NOT NULL,
  `fecha_corte` DATE NOT NULL,
  `descripcion` TEXT,
  `registrado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_proyecto_avances_proyecto_id_componente_fecha_corte` UNIQUE (`proyecto_id`, `componente`, `fecha_corte`),
  CONSTRAINT `fk_proyecto_avances_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_proyecto_avances_registrado_por_usuarios` FOREIGN KEY (`registrado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_proyecto_avances_1` CHECK (porcentaje BETWEEN 0 AND 100),
  KEY `idx_proyecto_avances_proyecto_id` (`proyecto_id`),
  KEY `idx_proyecto_avances_registrado_por` (`registrado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Avances generales y por componente visibles en el portal.';

-- Inventario: Etapas o fases de un proyecto.
CREATE TABLE `etapas_proyecto` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(30) NOT NULL,
  `nombre` VARCHAR(120) NOT NULL,
  `secuencia` INTEGER UNSIGNED NOT NULL,
  `fecha_inicio` DATE,
  `fecha_fin` DATE,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_etapas_proyecto_proyecto_id_codigo` UNIQUE (`proyecto_id`, `codigo`),
  CONSTRAINT `uk_etapas_proyecto_proyecto_id_secuencia` UNIQUE (`proyecto_id`, `secuencia`),
  CONSTRAINT `fk_etapas_proyecto_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_etapas_proyecto_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_etapas_proyecto_1` CHECK (estado IN ('ACTIVA','INACTIVA','CERRADA')),
  CONSTRAINT `ck_etapas_proyecto_2` CHECK (secuencia > 0),
  KEY `idx_etapas_proyecto_empresa_id` (`empresa_id`),
  KEY `idx_etapas_proyecto_proyecto_id` (`proyecto_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Etapas o fases de un proyecto.';

-- Inventario: Manzanas o bloques dentro de una etapa.
CREATE TABLE `manzanas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `etapa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(30) NOT NULL,
  `nombre` VARCHAR(120) NOT NULL,
  `secuencia` INTEGER UNSIGNED NOT NULL DEFAULT 1,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_manzanas_etapa_id_codigo` UNIQUE (`etapa_id`, `codigo`),
  CONSTRAINT `fk_manzanas_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_manzanas_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_manzanas_etapa_id_etapas_proyecto` FOREIGN KEY (`etapa_id`) REFERENCES `etapas_proyecto` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_manzanas_1` CHECK (estado IN ('ACTIVA','INACTIVA','CERRADA')),
  KEY `idx_manzanas_empresa_id` (`empresa_id`),
  KEY `idx_manzanas_proyecto_id` (`proyecto_id`),
  KEY `idx_manzanas_etapa_id` (`etapa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Manzanas o bloques dentro de una etapa.';

-- Inventario: Unidades inmobiliarias comercializables.
CREATE TABLE `lotes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `etapa_id` BIGINT UNSIGNED NOT NULL,
  `manzana_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `numero` VARCHAR(30) NOT NULL,
  `area_m2` DECIMAL(12,2) NOT NULL,
  `frente_m` DECIMAL(10,2),
  `fondo_m` DECIMAL(10,2),
  `valor_lista` DECIMAL(19,2) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
  `disponible` TINYINT(1) NOT NULL DEFAULT 1,
  `ubicacion_referencia` VARCHAR(300),
  `descripcion` TEXT,
  `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_lotes_proyecto_id_codigo` UNIQUE (`proyecto_id`, `codigo`),
  CONSTRAINT `uk_lotes_manzana_id_numero` UNIQUE (`manzana_id`, `numero`),
  CONSTRAINT `fk_lotes_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lotes_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lotes_etapa_id_etapas_proyecto` FOREIGN KEY (`etapa_id`) REFERENCES `etapas_proyecto` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_lotes_manzana_id_manzanas` FOREIGN KEY (`manzana_id`) REFERENCES `manzanas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_lotes_1` CHECK (area_m2 > 0),
  CONSTRAINT `ck_lotes_2` CHECK (valor_lista >= 0),
  CONSTRAINT `ck_lotes_3` CHECK (estado IN ('DISPONIBLE','RESERVADO','VENDIDO','BLOQUEADO')),
  KEY `idx_lotes_empresa_id_proyecto_id_estado` (`empresa_id`, `proyecto_id`, `estado`),
  KEY `idx_lotes_manzana_id_estado` (`manzana_id`, `estado`),
  KEY `idx_lotes_empresa_id` (`empresa_id`),
  KEY `idx_lotes_proyecto_id` (`proyecto_id`),
  KEY `idx_lotes_etapa_id` (`etapa_id`),
  KEY `idx_lotes_manzana_id` (`manzana_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Unidades inmobiliarias comercializables.';

-- Inventario: Historial inmutable de cambios de precio.
CREATE TABLE `lote_precios_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `valor_anterior` DECIMAL(19,2),
  `valor_nuevo` DECIMAL(19,2) NOT NULL,
  `vigente_desde` DATETIME(6) NOT NULL,
  `vigente_hasta` DATETIME(6),
  `motivo` VARCHAR(300) NOT NULL,
  `cambiado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_lote_precios_historial_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lote_precios_historial_cambiado_por_usuarios` FOREIGN KEY (`cambiado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_lote_precios_historial_1` CHECK (valor_nuevo >= 0),
  KEY `idx_lote_precios_historial_lote_id_vigente_desde` (`lote_id`, `vigente_desde`),
  KEY `idx_lote_precios_historial_lote_id` (`lote_id`),
  KEY `idx_lote_precios_historial_cambiado_por` (`cambiado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial inmutable de cambios de precio.';

-- Inventario: Trazabilidad de estados del lote.
CREATE TABLE `lote_historial_estados` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `estado_anterior` VARCHAR(20),
  `estado_nuevo` VARCHAR(20) NOT NULL,
  `entidad_origen` VARCHAR(40) NOT NULL,
  `entidad_origen_id` BIGINT UNSIGNED,
  `motivo` VARCHAR(300),
  `cambiado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_lote_historial_estados_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lote_historial_estados_cambiado_por_usuarios` FOREIGN KEY (`cambiado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_lote_historial_estados_lote_id_creado_en` (`lote_id`, `creado_en`),
  KEY `idx_lote_historial_estados_lote_id` (`lote_id`),
  KEY `idx_lote_historial_estados_cambiado_por` (`cambiado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Trazabilidad de estados del lote.';

-- Inventario: Planos, mapas e imágenes asociados a un lote.
CREATE TABLE `lote_archivos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(30) NOT NULL,
  `principal` TINYINT(1) NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_lote_archivos_lote_id_archivo_id` UNIQUE (`lote_id`, `archivo_id`),
  CONSTRAINT `fk_lote_archivos_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lote_archivos_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_lote_archivos_1` CHECK (tipo IN ('PLANO','IMAGEN','CERTIFICADO','OTRO')),
  KEY `idx_lote_archivos_lote_id` (`lote_id`),
  KEY `idx_lote_archivos_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Planos, mapas e imágenes asociados a un lote.';
