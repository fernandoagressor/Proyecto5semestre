-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V15: integridad de gerencia y control
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Control optimista para todos los agregados mutables del módulo.
ALTER TABLE `reglas_comision`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `activo`;

ALTER TABLE `comisiones`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `aprobado_por`;

ALTER TABLE `metas_comerciales`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `aprobado_por`;

ALTER TABLE `aprobaciones`
  ADD COLUMN `decision_por` BIGINT UNSIGNED NULL AFTER `asignado_a`,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `comentario_decision`,
  ADD CONSTRAINT `fk_aprobaciones_decision_por_usuarios`
    FOREIGN KEY (`decision_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  ADD KEY `idx_aprobaciones_decision_por` (`decision_por`);

ALTER TABLE `tareas`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `completada_en`;

ALTER TABLE `alertas_anomalia`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `resolucion`;

ALTER TABLE `reportes_generados`
  ADD COLUMN `detalle_error` VARCHAR(700) NULL AFTER `expira_en`,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `detalle_error`;

ALTER TABLE `presupuestos_proyecto`
  ADD COLUMN `creado_por` BIGINT UNSIGNED NULL AFTER `aprobado_por`,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `creado_por`,
  ADD CONSTRAINT `fk_presupuestos_proyecto_creado_por_usuarios`
    FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  ADD KEY `idx_presupuestos_proyecto_creado_por` (`creado_por`);

ALTER TABLE `costos_proyecto`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `aprobado_por`;

-- Índices de lectura ejecutiva y colas operativas.
ALTER TABLE `metas_comerciales`
  ADD KEY `idx_metas_empresa_periodo_estado` (`empresa_id`, `periodo`, `estado`);

ALTER TABLE `comisiones`
  ADD KEY `idx_comisiones_empresa_estado_fecha` (`empresa_id`, `estado`, `fecha_causacion`);

ALTER TABLE `tareas`
  ADD KEY `idx_tareas_empresa_estado_vencimiento` (`empresa_id`, `estado`, `fecha_vencimiento`);

ALTER TABLE `reportes_generados`
  ADD KEY `idx_reportes_empresa_estado_creado` (`empresa_id`, `estado`, `creado_en`);

ALTER TABLE `presupuestos_proyecto`
  ADD KEY `idx_presupuesto_empresa_periodo_estado`
    (`empresa_id`, `periodo_desde`, `periodo_hasta`, `estado`);

ALTER TABLE `costos_proyecto`
  ADD KEY `idx_costos_empresa_fecha_estado` (`empresa_id`, `fecha_costo`, `estado`);

-- La bitácora se administra únicamente mediante INSERT.
CREATE TRIGGER `trg_auditoria_eventos_no_update`
BEFORE UPDATE ON `auditoria_eventos`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'Los eventos de auditoria son inmutables';

CREATE TRIGGER `trg_auditoria_eventos_no_delete`
BEFORE DELETE ON `auditoria_eventos`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'Los eventos de auditoria no pueden eliminarse';

-- Separación explícita entre consulta y gestión sensible.
INSERT INTO `permisos`
(`codigo`,`modulo`,`nombre`,`descripcion`,`sensible`,`activo`) VALUES
('COMISIONES_REGLAS_GESTIONAR','Comisiones, metas y gerencia','Gestionar reglas de comisión','Crear y cerrar vigencias de reglas de comisión.',1,1),
('COMISIONES_GESTIONAR','Comisiones, metas y gerencia','Gestionar comisiones','Proyectar, causar y anular comisiones antes del pago.',1,1),
('METAS_GESTIONAR','Comisiones, metas y gerencia','Gestionar metas','Crear y editar metas comerciales en borrador.',1,1),
('TAREAS_REASIGNAR','Documentos, tareas y portal','Reasignar tareas','Reasignar tareas entre usuarios de la empresa.',1,1),
('PRESUPUESTOS_VER','Comisiones, metas y gerencia','Ver presupuestos','Consultar presupuestos y su ejecución.',0,1),
('COSTOS_PROYECTO_VER','Comisiones, metas y gerencia','Ver costos de proyecto','Consultar costos reales del proyecto.',0,1),
('AUDITORIA_ALERTAS_GESTIONAR','Reportes, auditoría y plataforma','Gestionar alertas de auditoría','Asignar, investigar y resolver alertas de anomalía.',1,1),
('REPORTES_GENERAR','Reportes, auditoría y plataforma','Generar reportes','Solicitar reportes operativos y gerenciales.',0,1),
('REPORTES_PROCESAR','Reportes, auditoría y plataforma','Procesar reportes','Completar o marcar fallida una solicitud desde un trabajador autorizado.',1,1);

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('COMISIONES_REGLAS_GESTIONAR','COMISIONES_GESTIONAR','METAS_GESTIONAR','TAREAS_REASIGNAR',
                  'PRESUPUESTOS_VER','COSTOS_PROYECTO_VER','AUDITORIA_ALERTAS_GESTIONAR',
                  'REPORTES_GENERAR','REPORTES_PROCESAR')
WHERE r.codigo = 'SUPER_ADMIN' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('COMISIONES_REGLAS_GESTIONAR','COMISIONES_GESTIONAR','METAS_GESTIONAR','TAREAS_REASIGNAR',
                  'PRESUPUESTOS_VER','COSTOS_PROYECTO_VER','COSTOS_PROYECTO_GESTIONAR',
                  'REPORTES_GENERAR')
WHERE r.codigo = 'ADMINISTRADOR' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('COMISIONES_GESTIONAR','METAS_GESTIONAR','PRESUPUESTOS_VER','COSTOS_PROYECTO_VER',
                  'COSTOS_PROYECTO_GESTIONAR','REPORTES_GENERAR')
WHERE r.codigo = 'GERENCIA' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('PRESUPUESTOS_VER','COSTOS_PROYECTO_VER','AUDITORIA_ALERTAS_GESTIONAR',
                  'REPORTES_GENERAR')
WHERE r.codigo = 'AUDITOR' AND r.empresa_id IS NULL;

INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('REPORTES_GENERAR')
WHERE r.codigo IN ('ASESOR_COMERCIAL','CARTERA','SERVICIO_CLIENTE')
  AND r.empresa_id IS NULL;
