-- Tablas espejo de lectura para el dashboard (rama 4: sincronización MongoDB -> MySQL).
-- Las llenan los proyectores del Change Stream cuando la colección de origen vive en MongoDB.
-- Solo guardan las columnas que consulta DashboardService; no tienen llaves foráneas porque
-- son copias: la fuente de verdad es el documento en MongoDB.

CREATE TABLE `lectura_solicitudes_pqrs` (
  `id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `tipo` VARCHAR(20) NOT NULL,
  `estado` VARCHAR(25) NOT NULL,
  `fecha_limite` DATETIME(6),
  `fecha_primera_respuesta` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lectura_pqrs_empresa_estado` (`empresa_id`, `estado`),
  KEY `idx_lectura_pqrs_empresa_creado` (`empresa_id`, `creado_en`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de lectura de la colección solicitudes_pqrs de MongoDB.';

CREATE TABLE `lectura_encuestas_satisfaccion` (
  `solicitud_id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `puntuacion` INTEGER UNSIGNED NOT NULL,
  `respondida_en` DATETIME(6) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`solicitud_id`),
  KEY `idx_lectura_encuestas_empresa_respondida` (`empresa_id`, `respondida_en`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de la encuesta embebida en cada documento de solicitudes_pqrs.';

CREATE TABLE `lectura_citas_servicio` (
  `id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL,
  `fecha_inicio` DATETIME(6) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lectura_citas_empresa_estado_inicio` (`empresa_id`, `estado`, `fecha_inicio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de lectura de la colección citas_servicio de MongoDB.';

CREATE TABLE `lectura_tickets_soporte` (
  `id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lectura_tickets_empresa_estado` (`empresa_id`, `estado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de lectura de la colección tickets_soporte de MongoDB.';

CREATE TABLE `lectura_tareas` (
  `id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lectura_tareas_empresa_estado` (`empresa_id`, `estado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de lectura de la colección tareas de MongoDB.';

CREATE TABLE `lectura_oportunidades` (
  `id` BIGINT UNSIGNED NOT NULL,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `etapa_actual` VARCHAR(40) NOT NULL,
  `estado` VARCHAR(20) NOT NULL,
  `probabilidad_cierre` DECIMAL(5,2) NOT NULL,
  `valor_estimado` DECIMAL(19,2),
  `fecha_cierre` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL,
  `sincronizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lectura_oportunidades_empresa_estado` (`empresa_id`, `estado`),
  KEY `idx_lectura_oportunidades_empresa_creado` (`empresa_id`, `creado_en`),
  KEY `idx_lectura_oportunidades_asesor` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Espejo de lectura de la colección oportunidades de MongoDB.';
