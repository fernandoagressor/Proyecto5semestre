-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V4: reservas y contratos
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Ventas: Bloqueo temporal de un lote a favor de un cliente.
CREATE TABLE `reservas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `oportunidad_id` BIGINT UNSIGNED,
  `simulacion_id` BIGINT UNSIGNED,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED,
  `fecha_reserva` DATETIME(6) NOT NULL,
  `fecha_vencimiento` DATETIME(6) NOT NULL,
  `valor_reserva` DECIMAL(19,2) NOT NULL,
  `forma_pago` VARCHAR(40),
  `estado_pago` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE_PAGO',
  `motivo_cancelacion` VARCHAR(400),
  `clave_lote_activa` BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN estado IN ('PENDIENTE_PAGO','ACTIVA') THEN lote_id ELSE NULL END) STORED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_reservas_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `uk_reservas_clave_lote_activa` UNIQUE (`clave_lote_activa`),
  CONSTRAINT `fk_reservas_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_reservas_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_reservas_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reservas_simulacion_id_simulaciones` FOREIGN KEY (`simulacion_id`) REFERENCES `simulaciones` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reservas_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reservas_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reservas_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_reservas_1` CHECK (fecha_vencimiento > fecha_reserva),
  CONSTRAINT `ck_reservas_2` CHECK (valor_reserva >= 0),
  CONSTRAINT `ck_reservas_3` CHECK (estado_pago IN ('PENDIENTE','CONFIRMADO','RECHAZADO')),
  CONSTRAINT `ck_reservas_4` CHECK (estado IN ('PENDIENTE_PAGO','ACTIVA','VENCIDA','CANCELADA','CONVERTIDA')),
  KEY `idx_reservas_empresa_id_estado_fecha_vencimiento` (`empresa_id`, `estado`, `fecha_vencimiento`),
  KEY `idx_reservas_cliente_id_estado` (`cliente_id`, `estado`),
  KEY `idx_reservas_empresa_id` (`empresa_id`),
  KEY `idx_reservas_proyecto_id` (`proyecto_id`),
  KEY `idx_reservas_oportunidad_id` (`oportunidad_id`),
  KEY `idx_reservas_simulacion_id` (`simulacion_id`),
  KEY `idx_reservas_cliente_id` (`cliente_id`),
  KEY `idx_reservas_lote_id` (`lote_id`),
  KEY `idx_reservas_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bloqueo temporal de un lote a favor de un cliente.';

-- Ventas: Cambios de estado y vigencia de una reserva.
CREATE TABLE `reserva_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reserva_id` BIGINT UNSIGNED NOT NULL,
  `estado_anterior` VARCHAR(20),
  `estado_nuevo` VARCHAR(20) NOT NULL,
  `motivo` VARCHAR(400),
  `usuario_id` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_reserva_historial_reserva_id_reservas` FOREIGN KEY (`reserva_id`) REFERENCES `reservas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_reserva_historial_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_reserva_historial_reserva_id_creado_en` (`reserva_id`, `creado_en`),
  KEY `idx_reserva_historial_reserva_id` (`reserva_id`),
  KEY `idx_reserva_historial_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cambios de estado y vigencia de una reserva.';

-- Ventas: Contratos de venta o promesa de compraventa.
CREATE TABLE `contratos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `oportunidad_id` BIGINT UNSIGNED,
  `reserva_id` BIGINT UNSIGNED,
  `cliente_principal_id` BIGINT UNSIGNED NOT NULL,
  `lote_id` BIGINT UNSIGNED NOT NULL,
  `asesor_id` BIGINT UNSIGNED,
  `tipo_contrato` VARCHAR(40) NOT NULL,
  `valor_lote` DECIMAL(19,2) NOT NULL,
  `otros_conceptos` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `descuento` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `valor_total` DECIMAL(19,2) NOT NULL,
  `cuota_inicial` DECIMAL(19,2) NOT NULL,
  `saldo_financiado` DECIMAL(19,2) NOT NULL,
  `numero_cuotas` INT UNSIGNED NOT NULL,
  `fecha_firma` DATE,
  `fecha_inicio` DATE NOT NULL,
  `fecha_finalizacion` DATE,
  `estado` VARCHAR(25) NOT NULL DEFAULT 'BORRADOR',
  `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `clave_lote_vigente` BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN estado IN ('PENDIENTE_FIRMA','VIGENTE') THEN lote_id ELSE NULL END) STORED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_contratos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `uk_contratos_reserva_id` UNIQUE (`reserva_id`),
  CONSTRAINT `uk_contratos_clave_lote_vigente` UNIQUE (`clave_lote_vigente`),
  CONSTRAINT `fk_contratos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_contratos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_contratos_oportunidad_id_oportunidades` FOREIGN KEY (`oportunidad_id`) REFERENCES `oportunidades` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_contratos_reserva_id_reservas` FOREIGN KEY (`reserva_id`) REFERENCES `reservas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_contratos_cliente_principal_id_clientes` FOREIGN KEY (`cliente_principal_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contratos_lote_id_lotes` FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contratos_asesor_id_usuarios` FOREIGN KEY (`asesor_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_contratos_1` CHECK (valor_lote >= 0 AND otros_conceptos >= 0 AND descuento >= 0 AND valor_total >= 0),
  CONSTRAINT `ck_contratos_2` CHECK (cuota_inicial >= 0 AND saldo_financiado >= 0),
  CONSTRAINT `ck_contratos_3` CHECK (numero_cuotas > 0),
  CONSTRAINT `ck_contratos_4` CHECK (estado IN ('BORRADOR','NEGOCIACION','PENDIENTE_FIRMA','VIGENTE','FINALIZADO','ANULADO')),
  KEY `idx_contratos_empresa_id_proyecto_id_estado` (`empresa_id`, `proyecto_id`, `estado`),
  KEY `idx_contratos_cliente_principal_id_estado` (`cliente_principal_id`, `estado`),
  KEY `idx_contratos_empresa_id` (`empresa_id`),
  KEY `idx_contratos_proyecto_id` (`proyecto_id`),
  KEY `idx_contratos_oportunidad_id` (`oportunidad_id`),
  KEY `idx_contratos_cliente_principal_id` (`cliente_principal_id`),
  KEY `idx_contratos_lote_id` (`lote_id`),
  KEY `idx_contratos_asesor_id` (`asesor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Contratos de venta o promesa de compraventa.';

-- Ventas: Compradores, copropietarios o beneficiarios de un contrato.
CREATE TABLE `contrato_clientes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `rol_en_contrato` VARCHAR(25) NOT NULL,
  `porcentaje_participacion` DECIMAL(5,2) NOT NULL DEFAULT 100,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_contrato_clientes_contrato_id_cliente_id_rol_en_contrato` UNIQUE (`contrato_id`, `cliente_id`, `rol_en_contrato`),
  CONSTRAINT `fk_contrato_clientes_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_contrato_clientes_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_contrato_clientes_1` CHECK (rol_en_contrato IN ('PRINCIPAL','COPROPIETARIO','BENEFICIARIO','APODERADO')),
  CONSTRAINT `ck_contrato_clientes_2` CHECK (porcentaje_participacion BETWEEN 0 AND 100),
  KEY `idx_contrato_clientes_contrato_id` (`contrato_id`),
  KEY `idx_contrato_clientes_cliente_id` (`cliente_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Compradores, copropietarios o beneficiarios de un contrato.';

-- Ventas: Versiones documentales y económicas del contrato.
CREATE TABLE `contrato_versiones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `numero_version` INT UNSIGNED NOT NULL,
  `archivo_id` BIGINT UNSIGNED,
  `datos_json` JSON NOT NULL,
  `motivo` VARCHAR(400) NOT NULL,
  `creado_por` BIGINT UNSIGNED,
  `vigente` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_contrato_versiones_contrato_id_numero_version` UNIQUE (`contrato_id`, `numero_version`),
  CONSTRAINT `fk_contrato_versiones_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_contrato_versiones_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_contrato_versiones_creado_por_usuarios` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_contrato_versiones_contrato_id` (`contrato_id`),
  KEY `idx_contrato_versiones_archivo_id` (`archivo_id`),
  KEY `idx_contrato_versiones_creado_por` (`creado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Versiones documentales y económicas del contrato.';

-- Ventas: Firmantes requeridos y estado de firma.
CREATE TABLE `contrato_firmantes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `persona_id` BIGINT UNSIGNED NOT NULL,
  `tipo_firmante` VARCHAR(25) NOT NULL,
  `orden_firma` INT UNSIGNED NOT NULL DEFAULT 1,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `fecha_programada` DATETIME(6),
  `fecha_firma` DATETIME(6),
  `archivo_firma_id` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_contrato_firmantes_contrato_id_persona_id_tipo_firmante` UNIQUE (`contrato_id`, `persona_id`, `tipo_firmante`),
  CONSTRAINT `fk_contrato_firmantes_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_contrato_firmantes_persona_id_personas` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contrato_firmantes_archivo_firma_id_archivos` FOREIGN KEY (`archivo_firma_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_contrato_firmantes_1` CHECK (estado IN ('PENDIENTE','ENVIADA','FIRMADA','RECHAZADA')),
  KEY `idx_contrato_firmantes_contrato_id` (`contrato_id`),
  KEY `idx_contrato_firmantes_persona_id` (`persona_id`),
  KEY `idx_contrato_firmantes_archivo_firma_id` (`archivo_firma_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Firmantes requeridos y estado de firma.';

-- Ventas: Eventos técnicos de firma electrónica o manuscrita.
CREATE TABLE `firmas_contrato` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contrato_firmante_id` BIGINT UNSIGNED NOT NULL,
  `metodo` VARCHAR(25) NOT NULL,
  `proveedor` VARCHAR(100),
  `referencia_externa` VARCHAR(180),
  `hash_documento` VARCHAR(64) NOT NULL,
  `ip_origen` VARCHAR(64),
  `firmada_en` DATETIME(6) NOT NULL,
  `estado_validacion` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_firmas_contrato_proveedor_referencia_externa` UNIQUE (`proveedor`, `referencia_externa`),
  CONSTRAINT `fk_firmas_contrato_contrato_firmante_id_contrato_firmantes` FOREIGN KEY (`contrato_firmante_id`) REFERENCES `contrato_firmantes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_firmas_contrato_1` CHECK (metodo IN ('MANUSCRITA','ELECTRONICA','DIGITAL')),
  CONSTRAINT `ck_firmas_contrato_2` CHECK (estado_validacion IN ('VALIDA','INVALIDA','PENDIENTE')),
  KEY `idx_firmas_contrato_contrato_firmante_id` (`contrato_firmante_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Eventos técnicos de firma electrónica o manuscrita.';
