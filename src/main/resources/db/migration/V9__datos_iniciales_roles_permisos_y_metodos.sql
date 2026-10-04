-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V9: datos iniciales roles permisos y metodos
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Roles base protegidos. Los roles personalizados se crean por empresa.
INSERT INTO `roles`
(`empresa_id`,`codigo`,`nombre`,`descripcion`,`es_sistema`,`protegido`,`activo`)
VALUES
(NULL,'SUPER_ADMIN','Super Admin','Gobierno de empresas, seguridad, configuración, auditoría, respaldos y soporte. Alcance: Global',1,1,1),
(NULL,'ADMINISTRADOR','Administrador','Operación inmobiliaria integral dentro del alcance asignado. Alcance: Empresa o proyecto',1,1,1),
(NULL,'ASESOR_COMERCIAL','Asesor Comercial','Prospectos, oportunidades, simulaciones, reservas y acompañamiento de venta. Alcance: Proyecto y registros propios',1,1,1),
(NULL,'CARTERA','Cartera','Cuotas, pagos, conciliación, mora, refinanciaciones y paz y salvos. Alcance: Empresa o proyecto',1,1,1),
(NULL,'CLIENTE','Cliente','Portal de contratos, lotes, pagos, documentos, citas y PQRS. Alcance: Información propia',1,1,1),
(NULL,'GERENCIA','Gerencia','Indicadores, rentabilidad, metas, aprobaciones y decisiones. Alcance: Empresa o proyectos asignados',1,1,1),
(NULL,'AUDITOR','Auditor','Consulta inmutable de accesos, cambios, finanzas y anomalías. Alcance: Empresa o proyecto',1,1,1),
(NULL,'SERVICIO_CLIENTE','Servicio Cliente','PQRS, citas, comunicaciones, documentos y base de conocimiento. Alcance: Empresa o proyecto',1,1,1);

-- Catálogo inicial de permisos.
INSERT INTO `permisos` (`codigo`,`modulo`,`nombre`,`descripcion`,`sensible`,`activo`) VALUES
('DASHBOARD_VER','Dashboard','Dashboard Ver','Capacidad dashboard ver.',0,1),
('DASHBOARD_COMERCIAL_VER','Dashboard','Dashboard Comercial Ver','Capacidad dashboard comercial ver.',0,1),
('DASHBOARD_CARTERA_VER','Dashboard','Dashboard Cartera Ver','Capacidad dashboard cartera ver.',0,1),
('DASHBOARD_GERENCIAL_VER','Dashboard','Dashboard Gerencial Ver','Capacidad dashboard gerencial ver.',0,1),
('DASHBOARD_SERVICIO_VER','Dashboard','Dashboard Servicio Ver','Capacidad dashboard servicio ver.',0,1),
('EMPRESAS_VER','Empresas y seguridad','Empresas Ver','Capacidad empresas ver.',0,1),
('EMPRESAS_CREAR','Empresas y seguridad','Empresas Crear','Capacidad empresas crear.',1,1),
('EMPRESAS_EDITAR','Empresas y seguridad','Empresas Editar','Capacidad empresas editar.',1,1),
('EMPRESAS_INACTIVAR','Empresas y seguridad','Empresas Inactivar','Capacidad empresas inactivar.',1,1),
('USUARIOS_VER','Empresas y seguridad','Usuarios Ver','Capacidad usuarios ver.',0,1),
('USUARIOS_CREAR','Empresas y seguridad','Usuarios Crear','Capacidad usuarios crear.',1,1),
('USUARIOS_EDITAR','Empresas y seguridad','Usuarios Editar','Capacidad usuarios editar.',1,1),
('USUARIOS_BLOQUEAR','Empresas y seguridad','Usuarios Bloquear','Capacidad usuarios bloquear.',1,1),
('ROLES_VER','Empresas y seguridad','Roles Ver','Capacidad roles ver.',0,1),
('ROLES_CREAR','Empresas y seguridad','Roles Crear','Capacidad roles crear.',1,1),
('ROLES_EDITAR','Empresas y seguridad','Roles Editar','Capacidad roles editar.',1,1),
('PERMISOS_ASIGNAR','Empresas y seguridad','Permisos Asignar','Capacidad permisos asignar.',1,1),
('ALCANCES_ASIGNAR','Empresas y seguridad','Alcances Asignar','Capacidad alcances asignar.',1,1),
('PROYECTOS_VER','Proyectos e inventario','Proyectos Ver','Capacidad proyectos ver.',0,1),
('PROYECTOS_CREAR','Proyectos e inventario','Proyectos Crear','Capacidad proyectos crear.',1,1),
('PROYECTOS_EDITAR','Proyectos e inventario','Proyectos Editar','Capacidad proyectos editar.',1,1),
('ETAPAS_VER','Proyectos e inventario','Etapas Ver','Capacidad etapas ver.',0,1),
('ETAPAS_GESTIONAR','Proyectos e inventario','Etapas Gestionar','Capacidad etapas gestionar.',1,1),
('MANZANAS_VER','Proyectos e inventario','Manzanas Ver','Capacidad manzanas ver.',0,1),
('MANZANAS_GESTIONAR','Proyectos e inventario','Manzanas Gestionar','Capacidad manzanas gestionar.',1,1),
('LOTES_VER','Proyectos e inventario','Lotes Ver','Capacidad lotes ver.',0,1),
('LOTES_DISPONIBILIDAD_VER','Proyectos e inventario','Lotes Disponibilidad Ver','Capacidad lotes disponibilidad ver.',0,1),
('LOTES_CREAR','Proyectos e inventario','Lotes Crear','Capacidad lotes crear.',1,1),
('LOTES_EDITAR','Proyectos e inventario','Lotes Editar','Capacidad lotes editar.',1,1),
('LOTES_CAMBIAR_ESTADO','Proyectos e inventario','Lotes Cambiar Estado','Capacidad lotes cambiar estado.',1,1),
('PRECIOS_LOTE_CAMBIAR','Proyectos e inventario','Precios Lote Cambiar','Capacidad precios lote cambiar.',1,1),
('CLIENTES_VER','Clientes y CRM','Clientes Ver','Capacidad clientes ver.',0,1),
('CLIENTES_CREAR','Clientes y CRM','Clientes Crear','Capacidad clientes crear.',0,1),
('CLIENTES_EDITAR','Clientes y CRM','Clientes Editar','Capacidad clientes editar.',0,1),
('CLIENTES_INACTIVAR','Clientes y CRM','Clientes Inactivar','Capacidad clientes inactivar.',1,1),
('PROSPECTOS_VER','Clientes y CRM','Prospectos Ver','Capacidad prospectos ver.',0,1),
('PROSPECTOS_CREAR','Clientes y CRM','Prospectos Crear','Capacidad prospectos crear.',0,1),
('PROSPECTOS_EDITAR','Clientes y CRM','Prospectos Editar','Capacidad prospectos editar.',0,1),
('PROSPECTOS_CALIFICAR','Clientes y CRM','Prospectos Calificar','Capacidad prospectos calificar.',0,1),
('PROSPECTOS_REASIGNAR','Clientes y CRM','Prospectos Reasignar','Capacidad prospectos reasignar.',1,1),
('OPORTUNIDADES_VER','Clientes y CRM','Oportunidades Ver','Capacidad oportunidades ver.',0,1),
('OPORTUNIDADES_CREAR','Clientes y CRM','Oportunidades Crear','Capacidad oportunidades crear.',0,1),
('OPORTUNIDADES_EDITAR','Clientes y CRM','Oportunidades Editar','Capacidad oportunidades editar.',0,1),
('OPORTUNIDADES_AVANZAR','Clientes y CRM','Oportunidades Avanzar','Capacidad oportunidades avanzar.',0,1),
('OPORTUNIDADES_CERRAR_PERDIDA','Clientes y CRM','Oportunidades Cerrar Perdida','Capacidad oportunidades cerrar perdida.',0,1),
('OPORTUNIDADES_REABRIR','Clientes y CRM','Oportunidades Reabrir','Capacidad oportunidades reabrir.',1,1),
('AGENDA_VER','Clientes y CRM','Agenda Ver','Capacidad agenda ver.',0,1),
('AGENDA_GESTIONAR','Clientes y CRM','Agenda Gestionar','Capacidad agenda gestionar.',0,1),
('SIMULACIONES_VER','Simulaciones y ventas','Simulaciones Ver','Capacidad simulaciones ver.',0,1),
('SIMULACIONES_CREAR','Simulaciones y ventas','Simulaciones Crear','Capacidad simulaciones crear.',0,1),
('SIMULACIONES_ENVIAR','Simulaciones y ventas','Simulaciones Enviar','Capacidad simulaciones enviar.',0,1),
('DESCUENTOS_EXTRAORDINARIOS_APROBAR','Simulaciones y ventas','Descuentos Extraordinarios Aprobar','Capacidad descuentos extraordinarios aprobar.',1,1),
('RESERVAS_VER','Simulaciones y ventas','Reservas Ver','Capacidad reservas ver.',0,1),
('RESERVAS_CREAR','Simulaciones y ventas','Reservas Crear','Capacidad reservas crear.',0,1),
('RESERVAS_EDITAR','Simulaciones y ventas','Reservas Editar','Capacidad reservas editar.',0,1),
('RESERVAS_CANCELAR','Simulaciones y ventas','Reservas Cancelar','Capacidad reservas cancelar.',1,1),
('RESERVAS_CONVERTIR_CONTRATO','Simulaciones y ventas','Reservas Convertir Contrato','Capacidad reservas convertir contrato.',0,1),
('CONTRATOS_VER','Simulaciones y ventas','Contratos Ver','Capacidad contratos ver.',0,1),
('CONTRATOS_CREAR','Simulaciones y ventas','Contratos Crear','Capacidad contratos crear.',0,1),
('CONTRATOS_EDITAR','Simulaciones y ventas','Contratos Editar','Capacidad contratos editar.',1,1),
('CONTRATOS_DESCARGAR','Simulaciones y ventas','Contratos Descargar','Capacidad contratos descargar.',0,1),
('CONTRATOS_ANULAR','Simulaciones y ventas','Contratos Anular','Capacidad contratos anular.',1,1),
('CONTRATOS_FIRMAS_GESTIONAR','Simulaciones y ventas','Contratos Firmas Gestionar','Capacidad contratos firmas gestionar.',1,1),
('CUOTAS_VER','Cartera','Cuotas Ver','Capacidad cuotas ver.',0,1),
('CUOTAS_PROGRAMAR','Cartera','Cuotas Programar','Capacidad cuotas programar.',1,1),
('PAGOS_VER','Cartera','Pagos Ver','Capacidad pagos ver.',0,1),
('PAGOS_CREAR','Cartera','Pagos Crear','Capacidad pagos crear.',0,1),
('PAGOS_CONFIRMAR','Cartera','Pagos Confirmar','Capacidad pagos confirmar.',1,1),
('PAGOS_RECHAZAR','Cartera','Pagos Rechazar','Capacidad pagos rechazar.',1,1),
('PAGOS_REVERSAR','Cartera','Pagos Reversar','Capacidad pagos reversar.',1,1),
('PAGOS_CARGA_MASIVA','Cartera','Pagos Carga Masiva','Capacidad pagos carga masiva.',1,1),
('CONCILIACION_BANCARIA','Cartera','Conciliacion Bancaria','Capacidad conciliacion bancaria.',1,1),
('MORA_VER','Cartera','Mora Ver','Capacidad mora ver.',0,1),
('MORA_GESTIONAR','Cartera','Mora Gestionar','Capacidad mora gestionar.',0,1),
('ESTADOS_CUENTA_GENERAR','Cartera','Estados Cuenta Generar','Capacidad estados cuenta generar.',0,1),
('REFINANCIACIONES_VER','Cartera','Refinanciaciones Ver','Capacidad refinanciaciones ver.',0,1),
('REFINANCIACIONES_PROPONER','Cartera','Refinanciaciones Proponer','Capacidad refinanciaciones proponer.',0,1),
('REFINANCIACIONES_APROBAR','Cartera','Refinanciaciones Aprobar','Capacidad refinanciaciones aprobar.',1,1),
('PAZ_SALVO_GENERAR','Cartera','Paz Salvo Generar','Capacidad paz salvo generar.',1,1),
('CIERRE_CARTERA','Cartera','Cierre Cartera','Capacidad cierre cartera.',1,1),
('DOCUMENTOS_VER','Documentos, tareas y portal','Documentos Ver','Capacidad documentos ver.',0,1),
('DOCUMENTOS_GENERAR','Documentos, tareas y portal','Documentos Generar','Capacidad documentos generar.',0,1),
('DOCUMENTOS_DESCARGAR','Documentos, tareas y portal','Documentos Descargar','Capacidad documentos descargar.',0,1),
('PLANTILLAS_DOCUMENTO_GESTIONAR','Documentos, tareas y portal','Plantillas Documento Gestionar','Capacidad plantillas documento gestionar.',1,1),
('TAREAS_VER','Documentos, tareas y portal','Tareas Ver','Capacidad tareas ver.',0,1),
('TAREAS_GESTIONAR','Documentos, tareas y portal','Tareas Gestionar','Capacidad tareas gestionar.',0,1),
('PORTAL_CLIENTE_HABILITAR','Documentos, tareas y portal','Portal Cliente Habilitar','Capacidad portal cliente habilitar.',1,1),
('PORTAL_CLIENTE_REENVIAR_ACCESO','Documentos, tareas y portal','Portal Cliente Reenviar Acceso','Capacidad portal cliente reenviar acceso.',1,1),
('COMISIONES_PROPIAS_VER','Comisiones, metas y gerencia','Comisiones Propias Ver','Capacidad comisiones propias ver.',0,1),
('COMISIONES_EQUIPO_VER','Comisiones, metas y gerencia','Comisiones Equipo Ver','Capacidad comisiones equipo ver.',1,1),
('COMISIONES_APROBAR','Comisiones, metas y gerencia','Comisiones Aprobar','Capacidad comisiones aprobar.',1,1),
('METAS_VER','Comisiones, metas y gerencia','Metas Ver','Capacidad metas ver.',0,1),
('METAS_APROBAR','Comisiones, metas y gerencia','Metas Aprobar','Capacidad metas aprobar.',1,1),
('VENTAS_GLOBALES_VER','Comisiones, metas y gerencia','Ventas Globales Ver','Capacidad ventas globales ver.',0,1),
('RENTABILIDAD_VER','Comisiones, metas y gerencia','Rentabilidad Ver','Capacidad rentabilidad ver.',1,1),
('FLUJO_CAJA_VER','Comisiones, metas y gerencia','Flujo Caja Ver','Capacidad flujo caja ver.',1,1),
('PRESUPUESTOS_APROBAR','Comisiones, metas y gerencia','Presupuestos Aprobar','Capacidad presupuestos aprobar.',1,1),
('COSTOS_PROYECTO_GESTIONAR','Comisiones, metas y gerencia','Costos Proyecto Gestionar','Capacidad costos proyecto gestionar.',1,1),
('SOLICITUDES_VER','Servicio al cliente','Solicitudes Ver','Capacidad solicitudes ver.',0,1),
('SOLICITUDES_CREAR','Servicio al cliente','Solicitudes Crear','Capacidad solicitudes crear.',0,1),
('SOLICITUDES_RESPONDER','Servicio al cliente','Solicitudes Responder','Capacidad solicitudes responder.',0,1),
('SOLICITUDES_ASIGNAR','Servicio al cliente','Solicitudes Asignar','Capacidad solicitudes asignar.',1,1),
('SOLICITUDES_CAMBIAR_ESTADO','Servicio al cliente','Solicitudes Cambiar Estado','Capacidad solicitudes cambiar estado.',0,1),
('SOLICITUDES_CERRAR','Servicio al cliente','Solicitudes Cerrar','Capacidad solicitudes cerrar.',1,1),
('CITAS_VER','Servicio al cliente','Citas Ver','Capacidad citas ver.',0,1),
('CITAS_CREAR','Servicio al cliente','Citas Crear','Capacidad citas crear.',0,1),
('CITAS_REPROGRAMAR','Servicio al cliente','Citas Reprogramar','Capacidad citas reprogramar.',0,1),
('DATOS_CLIENTE_SOLICITAR_CAMBIO','Servicio al cliente','Datos Cliente Solicitar Cambio','Capacidad datos cliente solicitar cambio.',0,1),
('DATOS_CLIENTE_APROBAR_CAMBIO','Servicio al cliente','Datos Cliente Aprobar Cambio','Capacidad datos cliente aprobar cambio.',1,1),
('COMUNICACIONES_CREAR','Servicio al cliente','Comunicaciones Crear','Capacidad comunicaciones crear.',0,1),
('COMUNICACIONES_MASIVAS_ENVIAR','Servicio al cliente','Comunicaciones Masivas Enviar','Capacidad comunicaciones masivas enviar.',1,1),
('BASE_CONOCIMIENTO_VER','Servicio al cliente','Base Conocimiento Ver','Capacidad base conocimiento ver.',0,1),
('BASE_CONOCIMIENTO_EDITAR','Servicio al cliente','Base Conocimiento Editar','Capacidad base conocimiento editar.',1,1),
('REPORTES_VER','Reportes, auditoría y plataforma','Reportes Ver','Capacidad reportes ver.',0,1),
('REPORTES_EXPORTAR','Reportes, auditoría y plataforma','Reportes Exportar','Capacidad reportes exportar.',0,1),
('REPORTES_FINANCIEROS_VER','Reportes, auditoría y plataforma','Reportes Financieros Ver','Capacidad reportes financieros ver.',1,1),
('AUDITORIA_VER','Reportes, auditoría y plataforma','Auditoria Ver','Capacidad auditoria ver.',1,1),
('AUDITORIA_DETALLE_VER','Reportes, auditoría y plataforma','Auditoria Detalle Ver','Capacidad auditoria detalle ver.',1,1),
('AUDITORIA_EXPORTAR','Reportes, auditoría y plataforma','Auditoria Exportar','Capacidad auditoria exportar.',1,1),
('ALERTAS_ANOMALIAS_VER','Reportes, auditoría y plataforma','Alertas Anomalias Ver','Capacidad alertas anomalias ver.',1,1),
('SOPORTE_VER','Reportes, auditoría y plataforma','Soporte Ver','Capacidad soporte ver.',0,1),
('SOPORTE_CREAR','Reportes, auditoría y plataforma','Soporte Crear','Capacidad soporte crear.',0,1),
('SOPORTE_RESPONDER','Reportes, auditoría y plataforma','Soporte Responder','Capacidad soporte responder.',0,1),
('SOPORTE_ASIGNAR','Reportes, auditoría y plataforma','Soporte Asignar','Capacidad soporte asignar.',1,1),
('SOPORTE_CAMBIAR_ESTADO','Reportes, auditoría y plataforma','Soporte Cambiar Estado','Capacidad soporte cambiar estado.',1,1),
('CONFIGURACION_EMPRESA_EDITAR','Reportes, auditoría y plataforma','Configuracion Empresa Editar','Capacidad configuracion empresa editar.',1,1),
('CONFIGURACION_GLOBAL_EDITAR','Reportes, auditoría y plataforma','Configuracion Global Editar','Capacidad configuracion global editar.',1,1),
('INTEGRACIONES_GESTIONAR','Reportes, auditoría y plataforma','Integraciones Gestionar','Capacidad integraciones gestionar.',1,1),
('RESPALDOS_GESTIONAR','Reportes, auditoría y plataforma','Respaldos Gestionar','Capacidad respaldos gestionar.',1,1);

-- SUPER_ADMIN conserva todos los permisos para operación interna de la plataforma.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r CROSS JOIN `permisos` p
WHERE r.codigo = 'SUPER_ADMIN' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para ADMINISTRADOR.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'EMPRESAS_VER', 'EMPRESAS_EDITAR', 'USUARIOS_VER', 'USUARIOS_CREAR', 'USUARIOS_EDITAR', 'USUARIOS_BLOQUEAR', 'ROLES_VER', 'ROLES_CREAR', 'ROLES_EDITAR', 'PERMISOS_ASIGNAR', 'ALCANCES_ASIGNAR', 'PROYECTOS_VER', 'PROYECTOS_CREAR', 'PROYECTOS_EDITAR', 'ETAPAS_VER', 'ETAPAS_GESTIONAR', 'MANZANAS_VER', 'MANZANAS_GESTIONAR', 'LOTES_VER', 'LOTES_DISPONIBILIDAD_VER', 'LOTES_CREAR', 'LOTES_EDITAR', 'LOTES_CAMBIAR_ESTADO', 'PRECIOS_LOTE_CAMBIAR', 'CLIENTES_VER', 'CLIENTES_CREAR', 'CLIENTES_EDITAR', 'CLIENTES_INACTIVAR', 'PROSPECTOS_VER', 'PROSPECTOS_CREAR', 'PROSPECTOS_EDITAR', 'PROSPECTOS_CALIFICAR', 'PROSPECTOS_REASIGNAR', 'OPORTUNIDADES_VER', 'OPORTUNIDADES_CREAR', 'OPORTUNIDADES_EDITAR', 'OPORTUNIDADES_AVANZAR', 'OPORTUNIDADES_CERRAR_PERDIDA', 'OPORTUNIDADES_REABRIR', 'AGENDA_VER', 'AGENDA_GESTIONAR', 'SIMULACIONES_VER', 'SIMULACIONES_CREAR', 'SIMULACIONES_ENVIAR', 'RESERVAS_VER', 'RESERVAS_CREAR', 'RESERVAS_EDITAR', 'RESERVAS_CANCELAR', 'RESERVAS_CONVERTIR_CONTRATO', 'CONTRATOS_VER', 'CONTRATOS_CREAR', 'CONTRATOS_EDITAR', 'CONTRATOS_DESCARGAR', 'CONTRATOS_FIRMAS_GESTIONAR', 'CUOTAS_VER', 'CUOTAS_PROGRAMAR', 'PAGOS_VER', 'PAGOS_CREAR', 'MORA_VER', 'MORA_GESTIONAR', 'ESTADOS_CUENTA_GENERAR', 'REFINANCIACIONES_VER', 'REFINANCIACIONES_PROPONER', 'DOCUMENTOS_VER', 'DOCUMENTOS_GENERAR', 'DOCUMENTOS_DESCARGAR', 'PLANTILLAS_DOCUMENTO_GESTIONAR', 'TAREAS_VER', 'TAREAS_GESTIONAR', 'PORTAL_CLIENTE_HABILITAR', 'PORTAL_CLIENTE_REENVIAR_ACCESO', 'COMISIONES_PROPIAS_VER', 'COMISIONES_EQUIPO_VER', 'METAS_VER', 'SOLICITUDES_VER', 'SOLICITUDES_CREAR', 'SOLICITUDES_RESPONDER', 'SOLICITUDES_ASIGNAR', 'SOLICITUDES_CAMBIAR_ESTADO', 'CITAS_VER', 'CITAS_CREAR', 'CITAS_REPROGRAMAR', 'DATOS_CLIENTE_SOLICITAR_CAMBIO', 'COMUNICACIONES_CREAR', 'BASE_CONOCIMIENTO_VER', 'BASE_CONOCIMIENTO_EDITAR', 'REPORTES_VER', 'REPORTES_EXPORTAR', 'SOPORTE_VER', 'SOPORTE_CREAR', 'SOPORTE_RESPONDER', 'SOPORTE_ASIGNAR', 'SOPORTE_CAMBIAR_ESTADO', 'CONFIGURACION_EMPRESA_EDITAR', 'INTEGRACIONES_GESTIONAR')
WHERE r.codigo = 'ADMINISTRADOR' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para ASESOR_COMERCIAL.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'DASHBOARD_COMERCIAL_VER', 'PROYECTOS_VER', 'ETAPAS_VER', 'MANZANAS_VER', 'LOTES_VER', 'LOTES_DISPONIBILIDAD_VER', 'CLIENTES_VER', 'CLIENTES_CREAR', 'CLIENTES_EDITAR', 'PROSPECTOS_VER', 'PROSPECTOS_CREAR', 'PROSPECTOS_EDITAR', 'PROSPECTOS_CALIFICAR', 'OPORTUNIDADES_VER', 'OPORTUNIDADES_CREAR', 'OPORTUNIDADES_EDITAR', 'OPORTUNIDADES_AVANZAR', 'OPORTUNIDADES_CERRAR_PERDIDA', 'AGENDA_VER', 'AGENDA_GESTIONAR', 'SIMULACIONES_VER', 'SIMULACIONES_CREAR', 'SIMULACIONES_ENVIAR', 'RESERVAS_VER', 'RESERVAS_CREAR', 'RESERVAS_EDITAR', 'RESERVAS_CANCELAR', 'CONTRATOS_VER', 'CONTRATOS_CREAR', 'CONTRATOS_DESCARGAR', 'CUOTAS_VER', 'CUOTAS_PROGRAMAR', 'PAGOS_VER', 'PAGOS_CREAR', 'DOCUMENTOS_VER', 'DOCUMENTOS_GENERAR', 'DOCUMENTOS_DESCARGAR', 'TAREAS_VER', 'TAREAS_GESTIONAR', 'COMISIONES_PROPIAS_VER', 'METAS_VER', 'REPORTES_VER', 'REPORTES_EXPORTAR')
WHERE r.codigo = 'ASESOR_COMERCIAL' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para CARTERA.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'DASHBOARD_CARTERA_VER', 'CLIENTES_VER', 'PROYECTOS_VER', 'CONTRATOS_VER', 'CONTRATOS_DESCARGAR', 'CUOTAS_VER', 'CUOTAS_PROGRAMAR', 'PAGOS_VER', 'PAGOS_CREAR', 'PAGOS_CONFIRMAR', 'PAGOS_RECHAZAR', 'PAGOS_REVERSAR', 'PAGOS_CARGA_MASIVA', 'CONCILIACION_BANCARIA', 'MORA_VER', 'MORA_GESTIONAR', 'ESTADOS_CUENTA_GENERAR', 'REFINANCIACIONES_VER', 'REFINANCIACIONES_PROPONER', 'PAZ_SALVO_GENERAR', 'CIERRE_CARTERA', 'DOCUMENTOS_VER', 'DOCUMENTOS_GENERAR', 'DOCUMENTOS_DESCARGAR', 'TAREAS_VER', 'TAREAS_GESTIONAR', 'REPORTES_VER', 'REPORTES_EXPORTAR', 'REPORTES_FINANCIEROS_VER')
WHERE r.codigo = 'CARTERA' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para CLIENTE.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'PROYECTOS_VER', 'ETAPAS_VER', 'MANZANAS_VER', 'LOTES_VER', 'CONTRATOS_VER', 'CONTRATOS_DESCARGAR', 'CUOTAS_VER', 'PAGOS_VER', 'PAGOS_CREAR', 'DOCUMENTOS_VER', 'DOCUMENTOS_DESCARGAR', 'SOLICITUDES_VER', 'SOLICITUDES_CREAR', 'SOLICITUDES_RESPONDER', 'CITAS_VER', 'CITAS_CREAR', 'CITAS_REPROGRAMAR', 'DATOS_CLIENTE_SOLICITAR_CAMBIO', 'BASE_CONOCIMIENTO_VER', 'SOPORTE_VER', 'SOPORTE_CREAR')
WHERE r.codigo = 'CLIENTE' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para GERENCIA.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'DASHBOARD_GERENCIAL_VER', 'PROYECTOS_VER', 'ETAPAS_VER', 'MANZANAS_VER', 'LOTES_VER', 'LOTES_DISPONIBILIDAD_VER', 'CLIENTES_VER', 'PROSPECTOS_VER', 'OPORTUNIDADES_VER', 'SIMULACIONES_VER', 'RESERVAS_VER', 'CONTRATOS_VER', 'CONTRATOS_DESCARGAR', 'CUOTAS_VER', 'PAGOS_VER', 'MORA_VER', 'REFINANCIACIONES_VER', 'REFINANCIACIONES_APROBAR', 'DESCUENTOS_EXTRAORDINARIOS_APROBAR', 'DOCUMENTOS_VER', 'DOCUMENTOS_DESCARGAR', 'COMISIONES_EQUIPO_VER', 'COMISIONES_APROBAR', 'METAS_VER', 'METAS_APROBAR', 'VENTAS_GLOBALES_VER', 'RENTABILIDAD_VER', 'FLUJO_CAJA_VER', 'PRESUPUESTOS_APROBAR', 'REPORTES_VER', 'REPORTES_EXPORTAR', 'REPORTES_FINANCIEROS_VER')
WHERE r.codigo = 'GERENCIA' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para AUDITOR.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'EMPRESAS_VER', 'USUARIOS_VER', 'ROLES_VER', 'PROYECTOS_VER', 'ETAPAS_VER', 'MANZANAS_VER', 'LOTES_VER', 'CLIENTES_VER', 'PROSPECTOS_VER', 'OPORTUNIDADES_VER', 'SIMULACIONES_VER', 'RESERVAS_VER', 'CONTRATOS_VER', 'CUOTAS_VER', 'PAGOS_VER', 'MORA_VER', 'REFINANCIACIONES_VER', 'DOCUMENTOS_VER', 'COMISIONES_EQUIPO_VER', 'METAS_VER', 'VENTAS_GLOBALES_VER', 'REPORTES_VER', 'REPORTES_EXPORTAR', 'REPORTES_FINANCIEROS_VER', 'AUDITORIA_VER', 'AUDITORIA_DETALLE_VER', 'AUDITORIA_EXPORTAR', 'ALERTAS_ANOMALIAS_VER')
WHERE r.codigo = 'AUDITOR' AND r.empresa_id IS NULL;

-- Permisos mínimos recomendados para SERVICIO_CLIENTE.
INSERT INTO `rol_permisos` (`rol_id`,`permiso_id`,`concedido`)
SELECT r.id, p.id, 1
FROM `roles` r JOIN `permisos` p
  ON p.codigo IN ('DASHBOARD_VER', 'DASHBOARD_SERVICIO_VER', 'PROYECTOS_VER', 'CLIENTES_VER', 'CONTRATOS_VER', 'PAGOS_VER', 'DOCUMENTOS_VER', 'DOCUMENTOS_GENERAR', 'DOCUMENTOS_DESCARGAR', 'TAREAS_VER', 'TAREAS_GESTIONAR', 'SOLICITUDES_VER', 'SOLICITUDES_CREAR', 'SOLICITUDES_RESPONDER', 'SOLICITUDES_ASIGNAR', 'SOLICITUDES_CAMBIAR_ESTADO', 'SOLICITUDES_CERRAR', 'CITAS_VER', 'CITAS_CREAR', 'CITAS_REPROGRAMAR', 'DATOS_CLIENTE_SOLICITAR_CAMBIO', 'COMUNICACIONES_CREAR', 'BASE_CONOCIMIENTO_VER', 'BASE_CONOCIMIENTO_EDITAR', 'REPORTES_VER', 'REPORTES_EXPORTAR')
WHERE r.codigo = 'SERVICIO_CLIENTE' AND r.empresa_id IS NULL;

-- Métodos de pago globales iniciales.
INSERT INTO `metodos_pago`
(`empresa_id`,`codigo`,`nombre`,`requiere_referencia`,`requiere_comprobante`,`activo`) VALUES
(NULL,'TRANSFERENCIA','Transferencia bancaria',1,1,1),
(NULL,'PSE','PSE',1,1,1),
(NULL,'TARJETA','Tarjeta',1,1,1),
(NULL,'EFECTIVO','Efectivo',0,0,1),
(NULL,'CONSIGNACION','Consignación',1,1,1);
