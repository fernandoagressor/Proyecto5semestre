-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V3: crm y simulaciones
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- CRM comercial: Personas interesadas antes de convertirse en clientes.
CREATE TABLE `prospectos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `persona_id` BIGINT UNSIGNED,
  `cliente_id` BIGINT UNSIGNED,
  `asesor_id` BIGINT UNSIGNED,
  `nombres` VARCHAR(160) NOT NULL,
  `telefono` VARCHAR(40),
  `correo` VARCHAR(180),
  `fuente` VARCHAR(80) NOT NULL,
  `presupuesto` DECIMAL(19,2),
  `temperatura` VARCHAR(15) NOT NULL DEFAULT 'FRIO',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'NUEVO',
  `observaciones` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_prospectos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_prospectos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_prospectos_persona_id_personas` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_prospectos_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_prospectos_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_prospectos_1` CHECK (temperatura IN ('FRIO','TIBIO','CALIENTE')),
  CONSTRAINT `ck_prospectos_2` CHECK (estado IN ('NUEVO','CONTACTADO','CALIFICADO','DESCARTADO','CONVERTIDO')),
  KEY `idx_prospectos_empresa_id_asesor_id_estado` (`empresa_id`, `asesor_id`, `estado`),
  KEY `idx_prospectos_telefono` (`telefono`),
  KEY `idx_prospectos_correo` (`correo`),
  KEY `idx_prospectos_empresa_id` (`empresa_id`),
  KEY `idx_prospectos_proyecto_id` (`proyecto_id`),
  KEY `idx_prospectos_persona_id` (`persona_id`),
  KEY `idx_prospectos_cliente_id` (`cliente_id`),
  KEY `idx_prospectos_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Personas interesadas antes de convertirse en clientes.';

-- CRM comercial: Unidad central del embudo de ventas.
CREATE TABLE `oportunidades` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `prospecto_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `etapa_actual` VARCHAR(40) NOT NULL DEFAULT 'PROSPECTO_RECIBIDO',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
  `probabilidad_cierre` DECIMAL(5,2) NOT NULL DEFAULT 5,
  `valor_estimado` DECIMAL(19,2),
  `fecha_ultima_actividad` DATETIME(6),
  `fecha_proxima_accion` DATETIME(6),
  `motivo_perdida` VARCHAR(300),
  `fecha_cierre` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_oportunidades_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_oportunidades_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oportunidades_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oportunidades_prospecto_id_prospectos` FOREIGN KEY (`prospecto_id`) REFERENCES `prospectos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_oportunidades_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_oportunidades_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_oportunidades_1` CHECK (estado IN ('ABIERTA','GANADA','PERDIDA','CANCELADA')),
  CONSTRAINT `ck_oportunidades_2` CHECK (probabilidad_cierre BETWEEN 0 AND 100),
  KEY `idx_oportunidades_asesor_id_estado_etapa_actual` (`asesor_id`, `estado`, `etapa_actual`),
  KEY `idx_oportunidades_proyecto_id_estado` (`proyecto_id`, `estado`),
  KEY `idx_oportunidades_empresa_id` (`empresa_id`),
  KEY `idx_oportunidades_proyecto_id` (`proyecto_id`),
  KEY `idx_oportunidades_prospecto_id` (`prospecto_id`),
  KEY `idx_oportunidades_cliente_id` (`cliente_id`),
  KEY `idx_oportunidades_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Unidad central del embudo de ventas.';

-- CRM comercial: Lotes de interés vinculados a una oportunidad.
CREATE TABLE `oportunidad_lotes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `prioridad` INTEGER UNSIGNED NOT NULL DEFAULT 1,
  `seleccionado` TINYINT(1) NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_oportunidad_lotes_oportunidad_id_lote_id` UNIQUE (`oportunidad_id`, `lote_id`),
  CONSTRAINT `fk_oportunidad_lotes_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oportunidad_lotes_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE RESTRICT,
  KEY `idx_oportunidad_lotes_oportunidad_id` (`oportunidad_id`),
  KEY `idx_oportunidad_lotes_lote_id` (`lote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lotes de interés vinculados a una oportunidad.';

-- CRM comercial: Historial de avance o retroceso de etapa.
CREATE TABLE `oportunidad_etapa_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `etapa_anterior` VARCHAR(40),
  `etapa_nueva` VARCHAR(40) NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `observacion` VARCHAR(500),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_oportunidad_etapa_historial_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oportunidad_etapa_historial_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_oportunidad_etapa_historial_oportunidad_id_creado_en` (`oportunidad_id`, `creado_en`),
  KEY `idx_oportunidad_etapa_historial_oportunidad_id` (`oportunidad_id`),
  KEY `idx_oportunidad_etapa_historial_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial de avance o retroceso de etapa.';

-- CRM comercial: Llamadas, mensajes, correos, visitas y seguimientos.
CREATE TABLE `actividades_comerciales` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(25) NOT NULL,
  `resultado` VARCHAR(120),
  `descripcion` TEXT NOT NULL,
  `fecha_actividad` DATETIME(6) NOT NULL,
  `proxima_accion` VARCHAR(200),
  `fecha_proxima_accion` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_actividades_comerciales_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_actividades_comerciales_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_actividades_comerciales_1` CHECK (tipo IN ('LLAMADA','MENSAJE','CORREO','VISITA','NOTA','PROPUESTA')),
  KEY `idx_actividades_comerciales_oportunidad_id_fecha_actividad` (`oportunidad_id`, `fecha_actividad`),
  KEY `idx_actividades_comerciales_asesor_id_fecha_proxima_accion` (`asesor_id`, `fecha_proxima_accion`),
  KEY `idx_actividades_comerciales_oportunidad_id` (`oportunidad_id`),
  KEY `idx_actividades_comerciales_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Llamadas, mensajes, correos, visitas y seguimientos.';

-- CRM comercial: Agenda comercial presencial o virtual.
CREATE TABLE `citas_comerciales` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(30) NOT NULL,
  `fecha_inicio` DATETIME(6) NOT NULL,
  `fecha_fin` DATETIME(6) NOT NULL,
  `lugar` VARCHAR(300),
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
  `resultado` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_citas_comerciales_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_citas_comerciales_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_citas_comerciales_1` CHECK (fecha_fin > fecha_inicio),
  CONSTRAINT `ck_citas_comerciales_2` CHECK (estado IN ('PROGRAMADA','CONFIRMADA','REALIZADA','CANCELADA','REPROGRAMADA','NO_ASISTIO')),
  KEY `idx_citas_comerciales_asesor_id_fecha_inicio` (`asesor_id`, `fecha_inicio`),
  KEY `idx_citas_comerciales_estado_fecha_inicio` (`estado`, `fecha_inicio`),
  KEY `idx_citas_comerciales_oportunidad_id` (`oportunidad_id`),
  KEY `idx_citas_comerciales_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agenda comercial presencial o virtual.';

-- CRM comercial: Evidencia de presentación de proyecto y materiales compartidos.
CREATE TABLE `presentaciones_comerciales` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED NOT NULL,
  `fecha_presentacion` DATETIME(6) NOT NULL,
  `resultado` VARCHAR(150),
  `nivel_interes` VARCHAR(15),
  `materiales_json` JSON,
  `observaciones` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_presentaciones_comerciales_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_presentaciones_comerciales_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_presentaciones_comerciales_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_presentaciones_comerciales_1` CHECK (nivel_interes IS NULL OR nivel_interes IN ('BAJO','MEDIO','ALTO')),
  KEY `idx_presentaciones_comerciales_oportunidad_id` (`oportunidad_id`),
  KEY `idx_presentaciones_comerciales_proyecto_id` (`proyecto_id`),
  KEY `idx_presentaciones_comerciales_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Evidencia de presentación de proyecto y materiales compartidos.';

-- CRM comercial: Propuestas económicas no contractuales.
CREATE TABLE `simulaciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `oportunidad_id` BIGINT UNSIGNED NOT NULL,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `creado_por` BIGINT UNSIGNED NOT NULL,
  `valor_lote` DECIMAL(19,2) NOT NULL,
  `descuento` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `cuota_inicial` DECIMAL(19,2) NOT NULL,
  `saldo_financiar` DECIMAL(19,2) NOT NULL,
  `numero_cuotas` INTEGER UNSIGNED NOT NULL,
  `tasa_efectiva_anual` DECIMAL(9,6) NOT NULL DEFAULT 0,
  `periodicidad` VARCHAR(20) NOT NULL DEFAULT 'MENSUAL',
  `vigente_hasta` DATETIME(6) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_simulaciones_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_simulaciones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_simulaciones_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_simulaciones_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_simulaciones_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_simulaciones_creado_por_usuarios` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_simulaciones_1` CHECK (valor_lote >= 0 AND descuento >= 0 AND cuota_inicial >= 0 AND saldo_financiar >= 0),
  CONSTRAINT `ck_simulaciones_2` CHECK (numero_cuotas > 0),
  CONSTRAINT `ck_simulaciones_3` CHECK (estado IN ('BORRADOR','ENVIADA','ACEPTADA','VENCIDA','CANCELADA')),
  KEY `idx_simulaciones_empresa_id` (`empresa_id`),
  KEY `idx_simulaciones_proyecto_id` (`proyecto_id`),
  KEY `idx_simulaciones_oportunidad_id` (`oportunidad_id`),
  KEY `idx_simulaciones_lote_id` (`lote_id`),
  KEY `idx_simulaciones_creado_por` (`creado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Propuestas económicas no contractuales.';

-- CRM comercial: Cronograma proyectado de una simulación.
CREATE TABLE `simulacion_cuotas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `simulacion_id` BIGINT UNSIGNED NOT NULL,
  `numero` INTEGER UNSIGNED NOT NULL,
  `fecha_vencimiento` DATE NOT NULL,
  `capital` DECIMAL(19,2) NOT NULL,
  `interes` DECIMAL(19,2) NOT NULL,
  `otros` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `valor_total` DECIMAL(19,2) NOT NULL,
  `saldo_proyectado` DECIMAL(19,2) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_simulacion_cuotas_simulacion_id_numero` UNIQUE (`simulacion_id`, `numero`),
  CONSTRAINT `fk_simulacion_cuotas_simulacion_id_simulaciones` FOREIGN KEY (`simulacion_id`) REFERENCES `simulaciones` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_simulacion_cuotas_1` CHECK (numero > 0),
  CONSTRAINT `ck_simulacion_cuotas_2` CHECK (valor_total >= 0),
  KEY `idx_simulacion_cuotas_simulacion_id` (`simulacion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cronograma proyectado de una simulación.';
