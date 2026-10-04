-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V7: servicio soporte notificaciones
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Servicio al cliente: Peticiones, quejas, reclamos, sugerencias y solicitudes.
CREATE TABLE `solicitudes_pqrs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `codigo` VARCHAR(50) NOT NULL,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `contrato_id` BIGINT UNSIGNED,
  `asignado_a` BIGINT UNSIGNED,
  `tipo` VARCHAR(20) NOT NULL,
  `categoria` VARCHAR(60) NOT NULL,
  `asunto` VARCHAR(200) NOT NULL,
  `descripcion` TEXT NOT NULL,
  `prioridad` VARCHAR(15) NOT NULL DEFAULT 'MEDIA',
  `estado` VARCHAR(25) NOT NULL DEFAULT 'ABIERTA',
  `fecha_limite` DATETIME(6),
  `fecha_primera_respuesta` DATETIME(6),
  `fecha_resolucion` DATETIME(6),
  `fecha_cierre` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_solicitudes_pqrs_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
  CONSTRAINT `fk_solicitudes_pqrs_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitudes_pqrs_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_solicitudes_pqrs_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_solicitudes_pqrs_contrato_id_contratos` FOREIGN KEY (`contrato_id`) REFERENCES `contratos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_solicitudes_pqrs_asignado_a_usuarios` FOREIGN KEY (`asignado_a`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_solicitudes_pqrs_1` CHECK (tipo IN ('PETICION','QUEJA','RECLAMO','SUGERENCIA','SOLICITUD')),
  CONSTRAINT `ck_solicitudes_pqrs_2` CHECK (prioridad IN ('BAJA','MEDIA','ALTA','CRITICA')),
  CONSTRAINT `ck_solicitudes_pqrs_3` CHECK (estado IN ('ABIERTA','EN_PROCESO','PENDIENTE_CLIENTE','PENDIENTE_TERCERO','RESUELTA','CERRADA','REABIERTA','CANCELADA')),
  KEY `idx_solicitudes_pqrs_asignado_a_estado_fecha_limite` (`asignado_a`, `estado`, `fecha_limite`),
  KEY `idx_solicitudes_pqrs_cliente_id_estado` (`cliente_id`, `estado`),
  KEY `idx_solicitudes_pqrs_empresa_id` (`empresa_id`),
  KEY `idx_solicitudes_pqrs_proyecto_id` (`proyecto_id`),
  KEY `idx_solicitudes_pqrs_cliente_id` (`cliente_id`),
  KEY `idx_solicitudes_pqrs_contrato_id` (`contrato_id`),
  KEY `idx_solicitudes_pqrs_asignado_a` (`asignado_a`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Peticiones, quejas, reclamos, sugerencias y solicitudes.';

-- Servicio al cliente: Conversación cronológica de una PQRS.
CREATE TABLE `solicitud_mensajes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `solicitud_id` BIGINT UNSIGNED NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `mensaje` TEXT NOT NULL,
  `es_interno` TINYINT(1) NOT NULL DEFAULT 0,
  `canal` VARCHAR(20) NOT NULL DEFAULT 'PORTAL',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_solicitud_mensajes_solicitud_id_solicitudes_pqrs` FOREIGN KEY (`solicitud_id`) REFERENCES `solicitudes_pqrs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitud_mensajes_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_solicitud_mensajes_1` CHECK (canal IN ('PORTAL','EMAIL','TELEFONO','INTERNO')),
  KEY `idx_solicitud_mensajes_solicitud_id_creado_en` (`solicitud_id`, `creado_en`),
  KEY `idx_solicitud_mensajes_solicitud_id` (`solicitud_id`),
  KEY `idx_solicitud_mensajes_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Conversación cronológica de una PQRS.';

-- Servicio al cliente: Archivos adjuntos a la solicitud o a un mensaje.
CREATE TABLE `solicitud_adjuntos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `solicitud_id` BIGINT UNSIGNED NOT NULL,
  `mensaje_id` BIGINT UNSIGNED,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_solicitud_adjuntos_solicitud_id_archivo_id` UNIQUE (`solicitud_id`, `archivo_id`),
  CONSTRAINT `fk_solicitud_adjuntos_solicitud_id_solicitudes_pqrs` FOREIGN KEY (`solicitud_id`) REFERENCES `solicitudes_pqrs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitud_adjuntos_mensaje_id_solicitud_mensajes` FOREIGN KEY (`mensaje_id`) REFERENCES `solicitud_mensajes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitud_adjuntos_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE CASCADE,
  KEY `idx_solicitud_adjuntos_solicitud_id` (`solicitud_id`),
  KEY `idx_solicitud_adjuntos_mensaje_id` (`mensaje_id`),
  KEY `idx_solicitud_adjuntos_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Archivos adjuntos a la solicitud o a un mensaje.';

-- Servicio al cliente: Historial de estados, prioridad y asignaciones de PQRS.
CREATE TABLE `solicitud_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `solicitud_id` BIGINT UNSIGNED NOT NULL,
  `campo` VARCHAR(40) NOT NULL,
  `valor_anterior` VARCHAR(500),
  `valor_nuevo` VARCHAR(500) NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `motivo` VARCHAR(500),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_solicitud_historial_solicitud_id_solicitudes_pqrs` FOREIGN KEY (`solicitud_id`) REFERENCES `solicitudes_pqrs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitud_historial_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_solicitud_historial_solicitud_id_creado_en` (`solicitud_id`, `creado_en`),
  KEY `idx_solicitud_historial_solicitud_id` (`solicitud_id`),
  KEY `idx_solicitud_historial_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial de estados, prioridad y asignaciones de PQRS.';

-- Servicio al cliente: Citas, visitas de entrega o visitas técnicas.
CREATE TABLE `citas_servicio` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `proyecto_id` BIGINT UNSIGNED,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `solicitud_id` BIGINT UNSIGNED,
  `responsable_id` BIGINT UNSIGNED,
  `tipo` VARCHAR(30) NOT NULL,
  `fecha_inicio` DATETIME(6) NOT NULL,
  `fecha_fin` DATETIME(6) NOT NULL,
  `lugar` VARCHAR(300),
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
  `resultado` TEXT,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_citas_servicio_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_citas_servicio_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_citas_servicio_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_citas_servicio_solicitud_id_solicitudes_pqrs` FOREIGN KEY (`solicitud_id`) REFERENCES `solicitudes_pqrs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_citas_servicio_responsable_id_usuarios` FOREIGN KEY (`responsable_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_citas_servicio_1` CHECK (fecha_fin > fecha_inicio),
  CONSTRAINT `ck_citas_servicio_2` CHECK (estado IN ('PROGRAMADA','CONFIRMADA','REALIZADA','CANCELADA','REPROGRAMADA')),
  KEY `idx_citas_servicio_empresa_id` (`empresa_id`),
  KEY `idx_citas_servicio_proyecto_id` (`proyecto_id`),
  KEY `idx_citas_servicio_cliente_id` (`cliente_id`),
  KEY `idx_citas_servicio_solicitud_id` (`solicitud_id`),
  KEY `idx_citas_servicio_responsable_id` (`responsable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Citas, visitas de entrega o visitas técnicas.';

-- Servicio al cliente: Bitácora de comunicaciones individuales o masivas.
CREATE TABLE `comunicaciones_cliente` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED,
  `enviado_por` BIGINT UNSIGNED,
  `canal` VARCHAR(20) NOT NULL,
  `asunto` VARCHAR(200),
  `contenido` TEXT NOT NULL,
  `es_masiva` TINYINT(1) NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `programada_para` DATETIME(6),
  `enviada_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_comunicaciones_cliente_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comunicaciones_cliente_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_comunicaciones_cliente_enviado_por_usuarios` FOREIGN KEY (`enviado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_comunicaciones_cliente_1` CHECK (canal IN ('EMAIL','SMS','WHATSAPP','PUSH','TELEFONO')),
  CONSTRAINT `ck_comunicaciones_cliente_2` CHECK (estado IN ('BORRADOR','PROGRAMADA','ENVIADA','FALLIDA')),
  KEY `idx_comunicaciones_cliente_empresa_id` (`empresa_id`),
  KEY `idx_comunicaciones_cliente_cliente_id` (`cliente_id`),
  KEY `idx_comunicaciones_cliente_enviado_por` (`enviado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bitácora de comunicaciones individuales o masivas.';

-- Servicio al cliente: Categorías de la base de conocimiento.
CREATE TABLE `categorias_conocimiento` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `nombre` VARCHAR(120) NOT NULL,
  `descripcion` VARCHAR(500),
  `orden` INTEGER UNSIGNED NOT NULL DEFAULT 1,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_categorias_conocimiento_ambito_empresa_id_nombre` UNIQUE (`ambito_empresa_id`, `nombre`),
  CONSTRAINT `fk_categorias_conocimiento_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  KEY `idx_categorias_conocimiento_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Categorías de la base de conocimiento.';

-- Servicio al cliente: Preguntas frecuentes y artículos operativos.
CREATE TABLE `articulos_conocimiento` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `categoria_id` BIGINT UNSIGNED NOT NULL,
  `titulo` VARCHAR(220) NOT NULL,
  `slug` VARCHAR(240) NOT NULL,
  `contenido` LONGTEXT NOT NULL,
  `visible_clientes` TINYINT(1) NOT NULL DEFAULT 0,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
  `autor_id` BIGINT UNSIGNED,
  `publicado_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_articulos_conocimiento_ambito_empresa_id_slug` UNIQUE (`ambito_empresa_id`, `slug`),
  CONSTRAINT `fk_articulos_conocimiento_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_articulos_conocimiento_categoria_id_categorias_c_496aefce` FOREIGN KEY (`categoria_id`) REFERENCES `categorias_conocimiento` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_articulos_conocimiento_autor_id_usuarios` FOREIGN KEY (`autor_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_articulos_conocimiento_1` CHECK (estado IN ('BORRADOR','PUBLICADO','ARCHIVADO')),
  KEY `idx_articulos_conocimiento_empresa_id` (`empresa_id`),
  KEY `idx_articulos_conocimiento_categoria_id` (`categoria_id`),
  KEY `idx_articulos_conocimiento_autor_id` (`autor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Preguntas frecuentes y artículos operativos.';

-- Servicio al cliente: Medición de satisfacción al cerrar atenciones.
CREATE TABLE `encuestas_satisfaccion` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED NOT NULL,
  `cliente_id` BIGINT UNSIGNED NOT NULL,
  `solicitud_id` BIGINT UNSIGNED,
  `puntuacion` INTEGER UNSIGNED NOT NULL,
  `comentario` TEXT,
  `respondida_en` DATETIME(6) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_encuestas_satisfaccion_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_encuestas_satisfaccion_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_encuestas_satisfaccion_solicitud_id_solicitudes_pqrs` FOREIGN KEY (`solicitud_id`) REFERENCES `solicitudes_pqrs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_encuestas_satisfaccion_1` CHECK (puntuacion BETWEEN 1 AND 5),
  KEY `idx_encuestas_satisfaccion_empresa_id` (`empresa_id`),
  KEY `idx_encuestas_satisfaccion_cliente_id` (`cliente_id`),
  KEY `idx_encuestas_satisfaccion_solicitud_id` (`solicitud_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Medición de satisfacción al cerrar atenciones.';

-- Soporte de plataforma: Incidentes y solicitudes técnicas de usuarios del sistema.
CREATE TABLE `tickets_soporte` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `proyecto_id` BIGINT UNSIGNED,
  `codigo` VARCHAR(50) NOT NULL,
  `solicitante_id` BIGINT UNSIGNED NOT NULL,
  `asignado_a` BIGINT UNSIGNED,
  `asunto` VARCHAR(200) NOT NULL,
  `categoria` VARCHAR(60) NOT NULL,
  `prioridad` VARCHAR(15) NOT NULL DEFAULT 'MEDIA',
  `descripcion` TEXT NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
  `fecha_primera_respuesta` DATETIME(6),
  `fecha_resolucion` DATETIME(6),
  `fecha_cierre` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_tickets_soporte_codigo` UNIQUE (`codigo`),
  CONSTRAINT `fk_tickets_soporte_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_tickets_soporte_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_tickets_soporte_solicitante_id_usuarios` FOREIGN KEY (`solicitante_id`) REFERENCES `usuarios` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tickets_soporte_asignado_a_usuarios` FOREIGN KEY (`asignado_a`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_tickets_soporte_1` CHECK (prioridad IN ('BAJA','MEDIA','ALTA','CRITICA')),
  CONSTRAINT `ck_tickets_soporte_2` CHECK (estado IN ('ABIERTO','EN_PROCESO','EN_ESPERA','RESUELTO','CERRADO','REABIERTO')),
  KEY `idx_tickets_soporte_asignado_a_estado` (`asignado_a`, `estado`),
  KEY `idx_tickets_soporte_solicitante_id_estado` (`solicitante_id`, `estado`),
  KEY `idx_tickets_soporte_empresa_id` (`empresa_id`),
  KEY `idx_tickets_soporte_proyecto_id` (`proyecto_id`),
  KEY `idx_tickets_soporte_solicitante_id` (`solicitante_id`),
  KEY `idx_tickets_soporte_asignado_a` (`asignado_a`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Incidentes y solicitudes técnicas de usuarios del sistema.';

-- Soporte de plataforma: Conversación del ticket técnico.
CREATE TABLE `ticket_mensajes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `mensaje` TEXT NOT NULL,
  `es_respuesta_interna` TINYINT(1) NOT NULL DEFAULT 0,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ticket_mensajes_ticket_id_tickets_soporte` FOREIGN KEY (`ticket_id`) REFERENCES `tickets_soporte` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ticket_mensajes_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_ticket_mensajes_ticket_id_creado_en` (`ticket_id`, `creado_en`),
  KEY `idx_ticket_mensajes_ticket_id` (`ticket_id`),
  KEY `idx_ticket_mensajes_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Conversación del ticket técnico.';

-- Soporte de plataforma: Adjuntos del ticket o de un mensaje.
CREATE TABLE `ticket_adjuntos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `mensaje_id` BIGINT UNSIGNED,
  `archivo_id` BIGINT UNSIGNED NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_ticket_adjuntos_ticket_id_archivo_id` UNIQUE (`ticket_id`, `archivo_id`),
  CONSTRAINT `fk_ticket_adjuntos_ticket_id_tickets_soporte` FOREIGN KEY (`ticket_id`) REFERENCES `tickets_soporte` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ticket_adjuntos_mensaje_id_ticket_mensajes` FOREIGN KEY (`mensaje_id`) REFERENCES `ticket_mensajes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ticket_adjuntos_archivo_id_archivos` FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`) ON DELETE CASCADE,
  KEY `idx_ticket_adjuntos_ticket_id` (`ticket_id`),
  KEY `idx_ticket_adjuntos_mensaje_id` (`mensaje_id`),
  KEY `idx_ticket_adjuntos_archivo_id` (`archivo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Adjuntos del ticket o de un mensaje.';

-- Soporte de plataforma: Historial de estados y asignaciones del ticket.
CREATE TABLE `ticket_historial` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `campo` VARCHAR(40) NOT NULL,
  `valor_anterior` VARCHAR(500),
  `valor_nuevo` VARCHAR(500) NOT NULL,
  `usuario_id` BIGINT UNSIGNED,
  `motivo` VARCHAR(500),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ticket_historial_ticket_id_tickets_soporte` FOREIGN KEY (`ticket_id`) REFERENCES `tickets_soporte` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ticket_historial_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  KEY `idx_ticket_historial_ticket_id_creado_en` (`ticket_id`, `creado_en`),
  KEY `idx_ticket_historial_ticket_id` (`ticket_id`),
  KEY `idx_ticket_historial_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Historial de estados y asignaciones del ticket.';

-- Notificaciones: Plantillas para correo, SMS, WhatsApp y push.
CREATE TABLE `plantillas_notificacion` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `ambito_empresa_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(empresa_id, 0)) STORED,
  `codigo` VARCHAR(80) NOT NULL,
  `canal` VARCHAR(20) NOT NULL,
  `asunto` VARCHAR(200),
  `contenido` LONGTEXT NOT NULL,
  `variables_json` JSON NOT NULL,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_plantillas_notificacion_ambito_empresa_id_codigo_canal` UNIQUE (`ambito_empresa_id`, `codigo`, `canal`),
  CONSTRAINT `fk_plantillas_notificacion_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_plantillas_notificacion_1` CHECK (canal IN ('EMAIL','SMS','WHATSAPP','PUSH')),
  KEY `idx_plantillas_notificacion_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Plantillas para correo, SMS, WhatsApp y push.';

-- Notificaciones: Notificaciones dentro de la aplicación.
CREATE TABLE `notificaciones` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `empresa_id` BIGINT UNSIGNED,
  `usuario_id` BIGINT UNSIGNED NOT NULL,
  `tipo` VARCHAR(40) NOT NULL,
  `titulo` VARCHAR(180) NOT NULL,
  `mensaje` TEXT NOT NULL,
  `entidad_tipo` VARCHAR(50),
  `entidad_id` BIGINT UNSIGNED,
  `prioridad` VARCHAR(15) NOT NULL DEFAULT 'MEDIA',
  `leida_en` DATETIME(6),
  `expira_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_notificaciones_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notificaciones_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_notificaciones_1` CHECK (prioridad IN ('BAJA','MEDIA','ALTA')),
  KEY `idx_notificaciones_usuario_id_leida_en_creado_en` (`usuario_id`, `leida_en`, `creado_en`),
  KEY `idx_notificaciones_empresa_id` (`empresa_id`),
  KEY `idx_notificaciones_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notificaciones dentro de la aplicación.';

-- Notificaciones: Intentos de entrega por canales externos.
CREATE TABLE `envios_notificacion` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `notificacion_id` BIGINT UNSIGNED,
  `plantilla_id` BIGINT UNSIGNED,
  `canal` VARCHAR(20) NOT NULL,
  `destinatario` VARCHAR(200) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  `proveedor` VARCHAR(100),
  `referencia_externa` VARCHAR(180),
  `intentos` INTEGER UNSIGNED NOT NULL DEFAULT 0,
  `ultimo_error` VARCHAR(700),
  `enviado_en` DATETIME(6),
  `entregado_en` DATETIME(6),
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_envios_notificacion_notificacion_id_notificaciones` FOREIGN KEY (`notificacion_id`) REFERENCES `notificaciones` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_envios_notificacion_plantilla_id_plantillas_notificacion` FOREIGN KEY (`plantilla_id`) REFERENCES `plantillas_notificacion` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_envios_notificacion_1` CHECK (estado IN ('PENDIENTE','ENVIADO','ENTREGADO','FALLIDO')),
  KEY `idx_envios_notificacion_estado_creado_en` (`estado`, `creado_en`),
  KEY `idx_envios_notificacion_notificacion_id` (`notificacion_id`),
  KEY `idx_envios_notificacion_plantilla_id` (`plantilla_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Intentos de entrega por canales externos.';
