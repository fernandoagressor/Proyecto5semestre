-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V5: cartera y pagos
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Cartera: Plan financiero vigente o preliminar de un contrato.
CREATE TABLE `planes_pago` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `version_plan` INT UNSIGNED NOT NULL,
  `capital_financiado` DECIMAL(19,2) NOT NULL,
  `tasa_efectiva_anual` DECIMAL(9,6) NOT NULL DEFAULT 0,
  `numero_cuotas` INTEGER UNSIGNED NOT NULL,
  `periodicidad` VARCHAR(20) NOT NULL DEFAULT 'MENSUAL',
  `fecha_primera_cuota` DATE NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `origen` VARCHAR(25) NOT NULL DEFAULT 'CONTRATO',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_planes_pago_contrato_id_version_plan` UNIQUE (`contrato_id`, `version_plan`),
  CONSTRAINT `fk_planes_pago_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_planes_pago_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_planes_pago_1` CHECK (capital_financiado >= 0),
  CONSTRAINT `ck_planes_pago_2` CHECK (numero_cuotas > 0),
  CONSTRAINT `ck_planes_pago_3` CHECK (origen IN ('CONTRATO','REFINANCIACION')),
  CONSTRAINT `ck_planes_pago_4` CHECK (estado IN ('BORRADOR','ACTIVO','REEMPLAZADO','CANCELADO')),
  KEY `idx_planes_pago_empresa_id` (`empresa_id`),
  KEY `idx_planes_pago_contrato_id` (`contrato_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Plan financiero vigente o preliminar de un contrato.';

-- Cartera: Obligaciones individuales del plan de pagos.
CREATE TABLE `cuotas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `plan_pago_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `numero` INT UNSIGNED NOT NULL,
  `fecha_vencimiento` DATE NOT NULL,
  `capital` DECIMAL(19,2) NOT NULL,
  `interes` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `otros_conceptos` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `valor_programado` DECIMAL(19,2) NOT NULL,
  `valor_pagado` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `saldo` DECIMAL(19,2) NOT NULL,
  `dias_mora` INT UNSIGNED NOT NULL DEFAULT 0,
  `interes_mora` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_cuotas_plan_pago_id_numero` UNIQUE (`plan_pago_id`, `numero`),
  CONSTRAINT `fk_cuotas_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cuotas_plan_pago_id_planes_pago` FOREIGN KEY (`plan_pago_id`) REFERENCES `planes_pago` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cuotas_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_cuotas_1` CHECK (numero > 0),
  CONSTRAINT `ck_cuotas_2` CHECK (valor_programado >= 0 AND valor_pagado >= 0 AND saldo >= 0),
  CONSTRAINT `ck_cuotas_3` CHECK (estado IN ('PENDIENTE','PARCIAL','PAGADA','VENCIDA','CONDONADA','CANCELADA')),
  KEY `idx_cuotas_empresa_id_estado_fecha_vencimiento` (`empresa_id`, `estado`, `fecha_vencimiento`),
  KEY `idx_cuotas_contrato_id_estado` (`contrato_id`, `estado`),
  KEY `idx_cuotas_empresa_id` (`empresa_id`),
  KEY `idx_cuotas_plan_pago_id` (`plan_pago_id`),
  KEY `idx_cuotas_contrato_id` (`contrato_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Obligaciones individuales del plan de pagos.';

-- Cartera: Documentos de cobro emitidos por contrato o cuota.
CREATE TABLE `facturas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `cuota_id` BIGINT UNSIGNED,
  `numero` VARCHAR(50) NOT NULL,
  `fecha_emision` DATE NOT NULL,
  `fecha_vencimiento` DATE NOT NULL,
  `subtotal` DECIMAL(19,2) NOT NULL,
  `impuestos` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `valor_total` DECIMAL(19,2) NOT NULL,
  `saldo` DECIMAL(19,2) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'EMITIDA',
  `archivo_id` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_facturas_empresa_id_numero` UNIQUE (`empresa_id`, `numero`),
  CONSTRAINT `fk_facturas_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_facturas_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_facturas_cuota_id_cuotas` FOREIGN KEY (`cuota_id`) REFERENCES `cuotas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_facturas_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_facturas_1` CHECK (fecha_vencimiento >= fecha_emision),
  CONSTRAINT `ck_facturas_2` CHECK (valor_total >= 0 AND saldo >= 0),
  CONSTRAINT `ck_facturas_3` CHECK (estado IN ('EMITIDA','PARCIAL','PAGADA','VENCIDA','ANULADA')),
  KEY `idx_facturas_empresa_id` (`empresa_id`),
  KEY `idx_facturas_contrato_id` (`contrato_id`),
  KEY `idx_facturas_cuota_id` (`cuota_id`),
  KEY `idx_facturas_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Documentos de cobro emitidos por contrato o cuota.';

-- Cartera: Catálogo de medios de pago globales o empresariales.
CREATE TABLE `metodos_pago` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `codigo` VARCHAR(40) NOT NULL,
  `nombre` VARCHAR(100) NOT NULL,
  `requiere_referencia` TINYINT(1) NOT NULL DEFAULT 1,
  `requiere_comprobante` TINYINT(1) NOT NULL DEFAULT 1,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_metodos_pago_ambito_empresa_id_codigo` UNIQUE (`ambito_empresa_id`, `codigo`),
  CONSTRAINT `fk_metodos_pago_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  KEY `idx_metodos_pago_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Catálogo de medios de pago globales o empresariales.';

-- Cartera: Pagos registrados, pendientes de validación o confirmados.
CREATE TABLE `pagos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `contrato_id` BIGINT UNSIGNED,
  `reserva_id` BIGINT UNSIGNED,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `metodo_pago_id` BIGINT UNSIGNED NOT NULL,
  `fecha_pago` DATETIME(6) NOT NULL,
  `valor` DECIMAL(19,2) NOT NULL,
  `referencia` VARCHAR(180),
  `comprobante_id` BIGINT UNSIGNED,
  `origen` VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `registrado_por` BIGINT UNSIGNED,
  `validado_por` BIGINT UNSIGNED,
  `validado_en` DATETIME(6),
  `observaciones` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_pagos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `uk_pagos_empresa_id_referencia` UNIQUE (`empresa_id`, `referencia`),
  CONSTRAINT `fk_pagos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pagos_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pagos_reserva_id_reservas` FOREIGN KEY (`reserva_id`) REFERENCES `reservas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pagos_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pagos_metodo_pago_id_metodos_pago` FOREIGN KEY (`metodo_pago_id`) REFERENCES `metodos_pago` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pagos_comprobante_id_archivos` FOREIGN KEY (`comprobante_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pagos_registrado_por_usuarios` FOREIGN KEY (`registrado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pagos_validado_por_usuarios` FOREIGN KEY (`validado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_pagos_2` CHECK (valor > 0),
  CONSTRAINT `ck_pagos_3` CHECK (origen IN ('MANUAL','PORTAL','MASIVO','PASARELA')),
  CONSTRAINT `ck_pagos_4` CHECK (estado IN ('PENDIENTE','EN_REVISION','CONFIRMADO','RECHAZADO','REVERSADO')),
  KEY `idx_pagos_empresa_id_estado_fecha_pago` (`empresa_id`, `estado`, `fecha_pago`),
  KEY `idx_pagos_contrato_id_estado` (`contrato_id`, `estado`),
  KEY `idx_pagos_empresa_id` (`empresa_id`),
  KEY `idx_pagos_contrato_id` (`contrato_id`),
  KEY `idx_pagos_reserva_id` (`reserva_id`),
  KEY `idx_pagos_cliente_id` (`cliente_id`),
  KEY `idx_pagos_metodo_pago_id` (`metodo_pago_id`),
  KEY `idx_pagos_comprobante_id` (`comprobante_id`),
  KEY `idx_pagos_registrado_por` (`registrado_por`),
  KEY `idx_pagos_validado_por` (`validado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Pagos registrados, pendientes de validación o confirmados.';

-- Cartera: Distribución de un pago confirmado entre cuotas o conceptos.
CREATE TABLE `pago_aplicaciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `pago_id` BIGINT UNSIGNED NOT NULL,
  `cuota_id` BIGINT UNSIGNED,
  `factura_id` BIGINT UNSIGNED,
  `concepto` VARCHAR(40) NOT NULL,
  `valor_aplicado` DECIMAL(19,2) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_pago_aplicaciones_pago_id_pagos` FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pago_aplicaciones_cuota_id_cuotas` FOREIGN KEY (`cuota_id`) REFERENCES `cuotas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pago_aplicaciones_factura_id_facturas` FOREIGN KEY (`factura_id`) REFERENCES `facturas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_pago_aplicaciones_2` CHECK (valor_aplicado > 0),
  KEY `idx_pago_aplicaciones_pago_id` (`pago_id`),
  KEY `idx_pago_aplicaciones_cuota_id` (`cuota_id`),
  KEY `idx_pago_aplicaciones_factura_id` (`factura_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Distribución de un pago confirmado entre cuotas o conceptos.';

-- Cartera: Historial completo de validación del pago.
CREATE TABLE `pago_historial_estados` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `pago_id` BIGINT UNSIGNED NOT NULL,
  `estado_anterior` VARCHAR(20),
  `estado_nuevo` VARCHAR(20) NOT NULL,
  `motivo` VARCHAR(500),
  `usuario_id` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_pago_historial_estados_pago_id_pagos` FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pago_historial_estados_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_pago_historial_estados_pago_id_creado_en` (`pago_id`, `creado_en`),
  KEY `idx_pago_historial_estados_pago_id` (`pago_id`),
  KEY `idx_pago_historial_estados_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial completo de validación del pago.';

-- Cartera: Reversiones autorizadas sin borrar el pago original.
CREATE TABLE `pago_reversiones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `pago_id` BIGINT UNSIGNED NOT NULL,
  `valor_revertido` DECIMAL(19,2) NOT NULL,
  `motivo` VARCHAR(500) NOT NULL,
  `evidencia_id` BIGINT UNSIGNED,
  `solicitado_por` BIGINT UNSIGNED NOT NULL,
  `aprobado_por` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA',
  `ejecutada_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_pago_reversiones_pago_id_pagos` FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pago_reversiones_evidencia_id_archivos` FOREIGN KEY (`evidencia_id`) REFERENCES `archivos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pago_reversiones_solicitado_por_usuarios` FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pago_reversiones_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_pago_reversiones_1` CHECK (valor_revertido > 0),
  CONSTRAINT `ck_pago_reversiones_2` CHECK (estado IN ('SOLICITADA','APROBADA','RECHAZADA','EJECUTADA')),
  KEY `idx_pago_reversiones_pago_id` (`pago_id`),
  KEY `idx_pago_reversiones_evidencia_id` (`evidencia_id`),
  KEY `idx_pago_reversiones_solicitado_por` (`solicitado_por`),
  KEY `idx_pago_reversiones_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Reversiones autorizadas sin borrar el pago original.';

-- Cartera: Cabecera de cargas masivas de pagos.
CREATE TABLE `importaciones_pago` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `importado_por` BIGINT UNSIGNED NOT NULL,
  `total_filas` INT UNSIGNED NOT NULL,
  `filas_validas` INT UNSIGNED NOT NULL DEFAULT 0,
  `filas_error` INT UNSIGNED NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'RECIBIDA',
  `procesada_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_importaciones_pago_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_importaciones_pago_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_importaciones_pago_importado_por_usuarios` FOREIGN KEY (`importado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_importaciones_pago_1` CHECK (estado IN ('RECIBIDA','VALIDADA','PROCESADA','FALLIDA')),
  KEY `idx_importaciones_pago_empresa_id` (`empresa_id`),
  KEY `idx_importaciones_pago_archivo_id` (`archivo_id`),
  KEY `idx_importaciones_pago_importado_por` (`importado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cabecera de cargas masivas de pagos.';

-- Cartera: Resultado por fila de una carga masiva.
CREATE TABLE `importacion_pago_detalles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `importacion_id` BIGINT UNSIGNED NOT NULL,
  `numero_fila` INT UNSIGNED NOT NULL,
  `datos_json` JSON NOT NULL,
  `pago_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL,
  `errores_json` JSON,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_importacion_pago_detalles_importacion_id_numero_fila` UNIQUE (`importacion_id`, `numero_fila`),
  CONSTRAINT `fk_importacion_pago_detalles_importacion_id_importa_299e2e6c` FOREIGN KEY (`importacion_id`) REFERENCES `importaciones_pago` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_importacion_pago_detalles_pago_id_pagos` FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_importacion_pago_detalles_1` CHECK (estado IN ('VALIDA','ERROR','DUPLICADA','PROCESADA')),
  KEY `idx_importacion_pago_detalles_importacion_id` (`importacion_id`),
  KEY `idx_importacion_pago_detalles_pago_id` (`pago_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Resultado por fila de una carga masiva.';

-- Cartera: Procesos de conciliación por cuenta y periodo.
CREATE TABLE `conciliaciones_bancarias` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `cuenta_bancaria` VARCHAR(100) NOT NULL,
  `fecha_desde` DATE NOT NULL,
  `fecha_hasta` DATE NOT NULL,
  `saldo_extracto` DECIMAL(19,2) NOT NULL,
  `total_conciliado` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `responsable_id` BIGINT UNSIGNED NOT NULL,
  `cerrada_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_conciliaciones_bancarias_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_conciliaciones_bancarias_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conciliaciones_bancarias_responsable_id_usuarios` FOREIGN KEY (`responsable_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_conciliaciones_bancarias_1` CHECK (fecha_hasta >= fecha_desde),
  CONSTRAINT `ck_conciliaciones_bancarias_2` CHECK (estado IN ('BORRADOR','EN_PROCESO','CERRADA','ANULADA')),
  KEY `idx_conciliaciones_bancarias_empresa_id` (`empresa_id`),
  KEY `idx_conciliaciones_bancarias_responsable_id` (`responsable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Procesos de conciliación por cuenta y periodo.';

-- Cartera: Movimientos bancarios y su relación con pagos.
CREATE TABLE `conciliacion_movimientos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conciliacion_id` BIGINT UNSIGNED NOT NULL,
  `fecha_movimiento` DATE NOT NULL,
  `referencia` VARCHAR(180) NOT NULL,
  `descripcion` VARCHAR(500),
  `valor` DECIMAL(19,2) NOT NULL,
  `pago_id` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `diferencia` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_conciliacion_movimientos_conciliacion_id_referencia_valor` UNIQUE (`conciliacion_id`, `referencia`, `valor`),
  CONSTRAINT `fk_conciliacion_movimientos_conciliacion_id_concili_6242382a` FOREIGN KEY (`conciliacion_id`) REFERENCES `conciliaciones_bancarias` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conciliacion_movimientos_pago_id_pagos` FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_conciliacion_movimientos_1` CHECK (estado IN ('PENDIENTE','CONCILIADO','DIFERENCIA','IGNORADO')),
  KEY `idx_conciliacion_movimientos_conciliacion_id` (`conciliacion_id`),
  KEY `idx_conciliacion_movimientos_pago_id` (`pago_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Movimientos bancarios y su relación con pagos.';

-- Cartera: Contactos y compromisos de cobro.
CREATE TABLE `gestiones_cobro` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `cuota_id` BIGINT UNSIGNED,
  `gestor_id` BIGINT UNSIGNED NOT NULL,
  `canal` VARCHAR(20) NOT NULL,
  `resultado` VARCHAR(120) NOT NULL,
  `detalle` TEXT,
  `promesa_pago_fecha` DATE,
  `promesa_pago_valor` DECIMAL(19,2),
  `fecha_gestion` DATETIME(6) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_gestiones_cobro_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gestiones_cobro_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_gestiones_cobro_cuota_id_cuotas` FOREIGN KEY (`cuota_id`) REFERENCES `cuotas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_gestiones_cobro_gestor_id_usuarios` FOREIGN KEY (`gestor_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_gestiones_cobro_1` CHECK (canal IN ('LLAMADA','SMS','EMAIL','WHATSAPP','VISITA')),
  KEY `idx_gestiones_cobro_contrato_id_fecha_gestion` (`contrato_id`, `fecha_gestion`),
  KEY `idx_gestiones_cobro_gestor_id_fecha_gestion` (`gestor_id`, `fecha_gestion`),
  KEY `idx_gestiones_cobro_empresa_id` (`empresa_id`),
  KEY `idx_gestiones_cobro_contrato_id` (`contrato_id`),
  KEY `idx_gestiones_cobro_cuota_id` (`cuota_id`),
  KEY `idx_gestiones_cobro_gestor_id` (`gestor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Contactos y compromisos de cobro.';

-- Cartera: Solicitudes y aprobaciones de refinanciación.
CREATE TABLE `refinanciaciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `saldo_refinanciado` DECIMAL(19,2) NOT NULL,
  `numero_cuotas_nuevo` INTEGER UNSIGNED NOT NULL,
  `tasa_nueva` DECIMAL(9,6) NOT NULL DEFAULT 0,
  `fecha_primera_cuota` DATE NOT NULL,
  `motivo` TEXT NOT NULL,
  `solicitado_por` BIGINT UNSIGNED NOT NULL,
  `aprobado_por` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA',
  `fecha_decision` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_refinanciaciones_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_refinanciaciones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_refinanciaciones_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_refinanciaciones_solicitado_por_usuarios` FOREIGN KEY (`solicitado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_refinanciaciones_aprobado_por_usuarios` FOREIGN KEY (`aprobado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_refinanciaciones_1` CHECK (saldo_refinanciado > 0),
  CONSTRAINT `ck_refinanciaciones_2` CHECK (numero_cuotas_nuevo > 0),
  CONSTRAINT `ck_refinanciaciones_3` CHECK (estado IN ('SOLICITADA','EN_ESTUDIO','APROBADA','RECHAZADA','APLICADA')),
  KEY `idx_refinanciaciones_empresa_id` (`empresa_id`),
  KEY `idx_refinanciaciones_contrato_id` (`contrato_id`),
  KEY `idx_refinanciaciones_solicitado_por` (`solicitado_por`),
  KEY `idx_refinanciaciones_aprobado_por` (`aprobado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Solicitudes y aprobaciones de refinanciación.';

-- Cartera: Cronograma propuesto para una refinanciación.
CREATE TABLE `refinanciacion_cuotas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `refinanciacion_id` BIGINT UNSIGNED NOT NULL,
  `numero` INTEGER UNSIGNED NOT NULL,
  `fecha_vencimiento` DATE NOT NULL,
  `capital` DECIMAL(19,2) NOT NULL,
  `interes` DECIMAL(19,2) NOT NULL,
  `valor_total` DECIMAL(19,2) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_refinanciacion_cuotas_refinanciacion_id_numero` UNIQUE (`refinanciacion_id`, `numero`),
  CONSTRAINT `fk_refinanciacion_cuotas_refinanciacion_id_refinanciaciones` FOREIGN KEY (`refinanciacion_id`) REFERENCES `refinanciaciones` (`id`) ON DELETE CASCADE,
  KEY `idx_refinanciacion_cuotas_refinanciacion_id` (`refinanciacion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cronograma propuesto para una refinanciación.';

-- Cartera: Estados de cuenta emitidos con corte histórico.
CREATE TABLE `estados_cuenta_generados` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `fecha_corte` DATE NOT NULL,
  `saldo_capital` DECIMAL(19,2) NOT NULL,
  `saldo_mora` DECIMAL(19,2) NOT NULL,
  `saldo_total` DECIMAL(19,2) NOT NULL,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `generado_por` BIGINT UNSIGNED,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_estados_cuenta_generados_contrato_id_fecha_corte` UNIQUE (`contrato_id`, `fecha_corte`),
  CONSTRAINT `fk_estados_cuenta_generados_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_estados_cuenta_generados_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_estados_cuenta_generados_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_estados_cuenta_generados_generado_por_usuarios` FOREIGN KEY (`generado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_estados_cuenta_generados_empresa_id` (`empresa_id`),
  KEY `idx_estados_cuenta_generados_contrato_id` (`contrato_id`),
  KEY `idx_estados_cuenta_generados_archivo_id` (`archivo_id`),
  KEY `idx_estados_cuenta_generados_generado_por` (`generado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Estados de cuenta emitidos con corte histórico.';

-- Cartera: Certificados emitidos cuando no existen obligaciones.
CREATE TABLE `paz_salvos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `fecha_emision` DATE NOT NULL,
  `saldo_verificado` DECIMAL(19,2) NOT NULL DEFAULT 0,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `generado_por` BIGINT UNSIGNED,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'VIGENTE',
  `motivo_anulacion` VARCHAR(400),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_paz_salvos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `uk_paz_salvos_contrato_id_fecha_emision` UNIQUE (`contrato_id`, `fecha_emision`),
  CONSTRAINT `fk_paz_salvos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_paz_salvos_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_paz_salvos_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_paz_salvos_generado_por_usuarios` FOREIGN KEY (`generado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_paz_salvos_1` CHECK (saldo_verificado = 0),
  CONSTRAINT `ck_paz_salvos_2` CHECK (estado IN ('VIGENTE','ANULADO')),
  KEY `idx_paz_salvos_empresa_id` (`empresa_id`),
  KEY `idx_paz_salvos_contrato_id` (`contrato_id`),
  KEY `idx_paz_salvos_archivo_id` (`archivo_id`),
  KEY `idx_paz_salvos_generado_por` (`generado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Certificados emitidos cuando no existen obligaciones.';

-- Cartera: Programación y resultado de recordatorios.
CREATE TABLE `recordatorios_cobro` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED NOT NULL,
  `cuota_id` BIGINT UNSIGNED,
  `canal` VARCHAR(20) NOT NULL,
  `programado_para` DATETIME(6) NOT NULL,
  `enviado_en` DATETIME(6),
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO',
  `resultado` VARCHAR(500),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_recordatorios_cobro_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_recordatorios_cobro_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recordatorios_cobro_cuota_id_cuotas` FOREIGN KEY (`cuota_id`) REFERENCES `cuotas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_recordatorios_cobro_1` CHECK (canal IN ('EMAIL','SMS','WHATSAPP','PUSH')),
  CONSTRAINT `ck_recordatorios_cobro_2` CHECK (estado IN ('PROGRAMADO','ENVIADO','FALLIDO','CANCELADO')),
  KEY `idx_recordatorios_cobro_estado_programado_para` (`estado`, `programado_para`),
  KEY `idx_recordatorios_cobro_empresa_id` (`empresa_id`),
  KEY `idx_recordatorios_cobro_contrato_id` (`contrato_id`),
  KEY `idx_recordatorios_cobro_cuota_id` (`cuota_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Programación y resultado de recordatorios.';

-- Cartera: Cierre mensual reproducible de indicadores de cartera.
CREATE TABLE `cierres_cartera` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `periodo` VARCHAR(7) NOT NULL,
  `saldo_total` DECIMAL(19,2) NOT NULL,
  `saldo_vencido` DECIMAL(19,2) NOT NULL,
  `recaudo_periodo` DECIMAL(19,2) NOT NULL,
  `indice_mora` DECIMAL(7,4) NOT NULL,
  `datos_json` JSON NOT NULL,
  `cerrado_por` BIGINT UNSIGNED NOT NULL,
  `cerrado_en` DATETIME(6) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_cierres_cartera_empresa_id_periodo` UNIQUE (`empresa_id`, `periodo`),
  CONSTRAINT `fk_cierres_cartera_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cierres_cartera_cerrado_por_usuarios` FOREIGN KEY (`cerrado_por`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  KEY `idx_cierres_cartera_empresa_id` (`empresa_id`),
  KEY `idx_cierres_cartera_cerrado_por` (`cerrado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cierre mensual reproducible de indicadores de cartera.';
