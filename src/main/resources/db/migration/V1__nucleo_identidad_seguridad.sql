-- Plataforma de Gestión Inmobiliaria
-- Repositorio objetivo: inmobiliaria-api
-- Migración V1: nucleo identidad seguridad
-- MySQL 8.0.16+ / recomendado MySQL 8.4 LTS

SET NAMES utf8mb4;
SET time_zone = '-05:00';
SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- Núcleo multiempresa: Organizaciones inmobiliarias que usan la plataforma.
CREATE TABLE `empresas` (
                            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            `codigo` VARCHAR(30) NOT NULL,
                            `nombre` VARCHAR(160) NOT NULL,
                            `nit` VARCHAR(30) NOT NULL,
                            `direccion` VARCHAR(240),
                            `ciudad` VARCHAR(100),
                            `departamento` VARCHAR(100),
                            `telefono` VARCHAR(40),
                            `correo` VARCHAR(180),
                            `logo_url` VARCHAR(500),
                            `zona_horaria` VARCHAR(60) NOT NULL DEFAULT 'America/Bogota',
                            `moneda` VARCHAR(3) NOT NULL DEFAULT 'COP',
                            `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
                            `limite_proyectos` INT UNSIGNED NOT NULL DEFAULT 1,
                            `limite_usuarios` INT UNSIGNED NOT NULL DEFAULT 5,
                            `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (`id`),
                            CONSTRAINT `uk_empresas_codigo` UNIQUE (`codigo`),
                            CONSTRAINT `uk_empresas_nit` UNIQUE (`nit`),
                            CONSTRAINT `ck_empresas_1` CHECK (estado IN ('ACTIVA','INACTIVA','SUSPENDIDA')),
                            CONSTRAINT `ck_empresas_2` CHECK (limite_proyectos > 0),
                            CONSTRAINT `ck_empresas_3` CHECK (limite_usuarios > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Organizaciones inmobiliarias que usan la plataforma.';

-- Identidad: Datos personales reutilizables por clientes, empleados y firmantes.
CREATE TABLE `personas` (
                            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            `tipo_persona` VARCHAR(15) NOT NULL DEFAULT 'NATURAL',
                            `tipo_documento` VARCHAR(20) NOT NULL,
                            `numero_documento` VARCHAR(40) NOT NULL,
                            `nombres` VARCHAR(120) NOT NULL,
                            `apellidos` VARCHAR(120),
                            `fecha_nacimiento` DATE,
                            `genero` VARCHAR(20),
                            `telefono` VARCHAR(40),
                            `telefono_alterno` VARCHAR(40),
                            `correo` VARCHAR(180),
                            `direccion` VARCHAR(240),
                            `ciudad` VARCHAR(100),
                            `departamento` VARCHAR(100),
                            `activo` TINYINT(1) NOT NULL DEFAULT 1,
                            `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (`id`),
                            CONSTRAINT `uk_personas_tipo_documento_numero_documento` UNIQUE (`tipo_documento`, `numero_documento`),
                            CONSTRAINT `ck_personas_1` CHECK (tipo_persona IN ('NATURAL','JURIDICA')),
                            KEY `idx_personas_correo` (`correo`),
                            KEY `idx_personas_nombres_apellidos` (`nombres`, `apellidos`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Datos personales reutilizables por clientes, empleados y firmantes.';

-- Seguridad: Roles protegidos del sistema y roles personalizados por empresa.
CREATE TABLE `roles` (
                         `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                         `empresa_id` BIGINT UNSIGNED NULL,
                         `ambito_empresa_id` BIGINT UNSIGNED
    GENERATED ALWAYS AS (COALESCE(`empresa_id`, 0)) STORED,
                         `codigo` VARCHAR(60) NOT NULL,
                         `nombre` VARCHAR(100) NOT NULL,
                         `descripcion` VARCHAR(500),
                         `es_sistema` TINYINT(1) NOT NULL DEFAULT 0,
                         `protegido` TINYINT(1) NOT NULL DEFAULT 0,
                         `activo` TINYINT(1) NOT NULL DEFAULT 1,
                         `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                         `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                         PRIMARY KEY (`id`),
                         CONSTRAINT `uk_roles_ambito_empresa_codigo` UNIQUE (`ambito_empresa_id`, `codigo`),
                         CONSTRAINT `fk_roles_empresa_id_empresas`
                             FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`)
                                 ON UPDATE RESTRICT ON DELETE RESTRICT,
                         KEY `idx_roles_codigo` (`codigo`),
                         KEY `idx_roles_empresa_id` (`empresa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Roles protegidos del sistema y roles personalizados por empresa.';

-- Seguridad: Catálogo global de capacidades autorizables.
CREATE TABLE `permisos` (
                            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            `codigo` VARCHAR(100) NOT NULL,
                            `modulo` VARCHAR(60) NOT NULL,
                            `nombre` VARCHAR(140) NOT NULL,
                            `descripcion` VARCHAR(500),
                            `sensible` TINYINT(1) NOT NULL DEFAULT 0,
                            `activo` TINYINT(1) NOT NULL DEFAULT 1,
                            `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (`id`),
                            CONSTRAINT `uk_permisos_codigo` UNIQUE (`codigo`),
                            KEY `idx_permisos_modulo` (`modulo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Catálogo global de capacidades autorizables.';

-- Seguridad: Relación muchos a muchos entre roles y permisos.
CREATE TABLE `rol_permisos` (
                                `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                `rol_id` BIGINT UNSIGNED NOT NULL,
                                `permiso_id` BIGINT UNSIGNED NOT NULL,
                                `concedido` TINYINT(1) NOT NULL DEFAULT 1,
                                `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                PRIMARY KEY (`id`),
                                CONSTRAINT `uk_rol_permisos_rol_id_permiso_id` UNIQUE (`rol_id`, `permiso_id`),
                                CONSTRAINT `fk_rol_permisos_rol_id_roles`
                                    FOREIGN KEY (`rol_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
                                CONSTRAINT `fk_rol_permisos_permiso_id_permisos`
                                    FOREIGN KEY (`permiso_id`) REFERENCES `permisos` (`id`) ON DELETE CASCADE,
                                KEY `idx_rol_permisos_rol_id` (`rol_id`),
                                KEY `idx_rol_permisos_permiso_id` (`permiso_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Relación muchos a muchos entre roles y permisos.';

-- Seguridad: Cuentas autenticables internas y del portal del cliente.
CREATE TABLE `usuarios` (
                            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            `empresa_id` BIGINT UNSIGNED,
                            `persona_id` BIGINT UNSIGNED NOT NULL,
                            `nombre_usuario` VARCHAR(80) NOT NULL,
                            `correo` VARCHAR(180) NOT NULL,
                            `password_hash` VARCHAR(255) NOT NULL,
                            `tipo_usuario` VARCHAR(20) NOT NULL DEFAULT 'INTERNO',
                            `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
                            `requiere_cambio_password` TINYINT(1) NOT NULL DEFAULT 1,
                            `mfa_habilitado` TINYINT(1) NOT NULL DEFAULT 0,
                            `ultimo_acceso_en` DATETIME(6),
                            `intentos_fallidos` INTEGER UNSIGNED NOT NULL DEFAULT 0,
                            `bloqueado_hasta` DATETIME(6),
                            `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (`id`),
                            CONSTRAINT `uk_usuarios_nombre_usuario` UNIQUE (`nombre_usuario`),
                            CONSTRAINT `uk_usuarios_correo` UNIQUE (`correo`),
                            CONSTRAINT `fk_usuarios_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE SET NULL,
                            CONSTRAINT `fk_usuarios_persona_id_personas` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`) ON DELETE RESTRICT,
                            CONSTRAINT `ck_usuarios_1` CHECK (tipo_usuario IN ('INTERNO','CLIENTE','SISTEMA')),
                            CONSTRAINT `ck_usuarios_2` CHECK (estado IN ('ACTIVO','INACTIVO','BLOQUEADO')),
                            KEY `idx_usuarios_empresa_id_estado` (`empresa_id`, `estado`),
                            KEY `idx_usuarios_empresa_id` (`empresa_id`),
                            KEY `idx_usuarios_persona_id` (`persona_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cuentas autenticables internas y del portal del cliente.';

-- Seguridad: Roles asignados a cada usuario.
CREATE TABLE `usuario_roles` (
                                 `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                 `usuario_id` BIGINT UNSIGNED NOT NULL,
                                 `rol_id` BIGINT UNSIGNED NOT NULL,
                                 `principal` TINYINT(1) NOT NULL DEFAULT 0,
                                 `asignado_por` BIGINT UNSIGNED,
                                 `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                 `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                 PRIMARY KEY (`id`),
                                 CONSTRAINT `uk_usuario_roles_usuario_id_rol_id` UNIQUE (`usuario_id`, `rol_id`),
                                 CONSTRAINT `fk_usuario_roles_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
                                 CONSTRAINT `fk_usuario_roles_rol_id_roles` FOREIGN KEY (`rol_id`) REFERENCES `roles` (`id`) ON DELETE RESTRICT,
                                 CONSTRAINT `fk_usuario_roles_asignado_por_usuarios` FOREIGN KEY (`asignado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                                 KEY `idx_usuario_roles_usuario_id` (`usuario_id`),
                                 KEY `idx_usuario_roles_rol_id` (`rol_id`),
                                 KEY `idx_usuario_roles_asignado_por` (`asignado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Roles asignados a cada usuario.';

-- Proyectos: Proyectos inmobiliarios administrados por cada empresa.
CREATE TABLE `proyectos` (
                             `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             `empresa_id` BIGINT UNSIGNED NOT NULL,
                             `codigo` VARCHAR(40) NOT NULL,
                             `nombre` VARCHAR(180) NOT NULL,
                             `descripcion` TEXT,
                             `ciudad` VARCHAR(100) NOT NULL,
                             `departamento` VARCHAR(100) NOT NULL,
                             `direccion` VARCHAR(240),
                             `fecha_inicio` DATE,
                             `fecha_fin_estimada` DATE,
                             `estado` VARCHAR(25) NOT NULL DEFAULT 'PLANEACION',
                             `porcentaje_avance` DECIMAL(5,2) NOT NULL DEFAULT 0,
                             `max_lotes` INT UNSIGNED,
                             `imagen_principal_url` VARCHAR(500),
                             `gerente_id` BIGINT UNSIGNED,
                             `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                             `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                             PRIMARY KEY (`id`),
                             CONSTRAINT `uk_proyectos_empresa_id_codigo` UNIQUE (`empresa_id`, `codigo`),
                             CONSTRAINT `fk_proyectos_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
                             CONSTRAINT `fk_proyectos_gerente_id_usuarios` FOREIGN KEY (`gerente_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                             CONSTRAINT `ck_proyectos_1` CHECK (porcentaje_avance BETWEEN 0 AND 100),
                             CONSTRAINT `ck_proyectos_2` CHECK (estado IN ('PLANEACION','ACTIVO','PAUSADO','FINALIZADO','INACTIVO')),
                             KEY `idx_proyectos_empresa_id_estado` (`empresa_id`, `estado`),
                             KEY `idx_proyectos_empresa_id` (`empresa_id`),
                             KEY `idx_proyectos_gerente_id` (`gerente_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Proyectos inmobiliarios administrados por cada empresa.';

-- Seguridad: Ámbito autorizado por empresa, proyecto o registros propios.
CREATE TABLE `usuario_alcances` (
                                    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                    `usuario_id` BIGINT UNSIGNED NOT NULL,
                                    `empresa_id` BIGINT UNSIGNED NOT NULL,
                                    `proyecto_id` BIGINT UNSIGNED,
                                    `ambito_proyecto_id` BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(proyecto_id, 0)) STORED,
                                    `tipo_alcance` VARCHAR(20) NOT NULL,
                                    `asignado_por` BIGINT UNSIGNED,
                                    `vigente_desde` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                    `vigente_hasta` DATETIME(6),
                                    `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                    `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                    PRIMARY KEY (`id`),
                                    CONSTRAINT `uk_usuario_alcances_usuario_id_empresa_id_ambito_pr_bef4f9fa` UNIQUE (`usuario_id`, `empresa_id`, `ambito_proyecto_id`, `tipo_alcance`),
                                    CONSTRAINT `fk_usuario_alcances_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
                                    CONSTRAINT `fk_usuario_alcances_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
                                    CONSTRAINT `fk_usuario_alcances_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE RESTRICT,
                                    CONSTRAINT `fk_usuario_alcances_asignado_por_usuarios` FOREIGN KEY (`asignado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                                    CONSTRAINT `ck_usuario_alcances_1` CHECK (tipo_alcance IN ('EMPRESA','PROYECTO','PROPIOS')),
                                    CONSTRAINT `ck_usuario_alcances_2` CHECK (((tipo_alcance = 'EMPRESA' AND proyecto_id IS NULL) OR (tipo_alcance = 'PROYECTO' AND proyecto_id IS NOT NULL) OR tipo_alcance = 'PROPIOS')),
                                    CONSTRAINT `ck_usuario_alcances_3` CHECK ((vigente_hasta IS NULL OR vigente_hasta > vigente_desde)),
                                    KEY `idx_usuario_alcances_usuario_id` (`usuario_id`),
                                    KEY `idx_usuario_alcances_empresa_id` (`empresa_id`),
                                    KEY `idx_usuario_alcances_proyecto_id` (`proyecto_id`),
                                    KEY `idx_usuario_alcances_asignado_por` (`asignado_por`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ámbito autorizado por empresa, proyecto o registros propios.';

-- Identidad: Vínculo laboral o contractual de usuarios internos.
CREATE TABLE `empleados` (
                             `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             `empresa_id` BIGINT UNSIGNED NOT NULL,
                             `persona_id` BIGINT UNSIGNED NOT NULL,
                             `usuario_id` BIGINT UNSIGNED,
                             `codigo_empleado` VARCHAR(40) NOT NULL,
                             `cargo` VARCHAR(120) NOT NULL,
                             `fecha_ingreso` DATE NOT NULL,
                             `fecha_retiro` DATE,
                             `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
                             `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                             `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                             PRIMARY KEY (`id`),
                             CONSTRAINT `uk_empleados_empresa_id_codigo_empleado` UNIQUE (`empresa_id`, `codigo_empleado`),
                             CONSTRAINT `uk_empleados_empresa_id_persona_id` UNIQUE (`empresa_id`, `persona_id`),
                             CONSTRAINT `uk_empleados_usuario_id` UNIQUE (`usuario_id`),
                             CONSTRAINT `fk_empleados_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
                             CONSTRAINT `fk_empleados_persona_id_personas` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`) ON DELETE RESTRICT,
                             CONSTRAINT `fk_empleados_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                             CONSTRAINT `ck_empleados_1` CHECK (estado IN ('ACTIVO','INACTIVO','RETIRADO')),
                             KEY `idx_empleados_empresa_id` (`empresa_id`),
                             KEY `idx_empleados_persona_id` (`persona_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vínculo laboral o contractual de usuarios internos.';

-- Clientes: Perfil de cliente dentro de una empresa.
CREATE TABLE `clientes` (
                            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            `empresa_id` BIGINT UNSIGNED NOT NULL,
                            `persona_id` BIGINT UNSIGNED NOT NULL,
                            `usuario_id` BIGINT UNSIGNED,
                            `codigo_cliente` VARCHAR(40) NOT NULL,
                            `origen` VARCHAR(80),
                            `observaciones` TEXT,
                            `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
                            `fecha_alta` DATE NOT NULL DEFAULT (CURRENT_DATE),
                            `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (`id`),
                            CONSTRAINT `uk_clientes_empresa_id_persona_id` UNIQUE (`empresa_id`, `persona_id`),
                            CONSTRAINT `uk_clientes_empresa_id_codigo_cliente` UNIQUE (`empresa_id`, `codigo_cliente`),
                            CONSTRAINT `uk_clientes_usuario_id` UNIQUE (`usuario_id`),
                            CONSTRAINT `fk_clientes_empresa_id_empresas` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`) ON DELETE CASCADE,
                            CONSTRAINT `fk_clientes_persona_id_personas` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`) ON DELETE RESTRICT,
                            CONSTRAINT `fk_clientes_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                            CONSTRAINT `ck_clientes_1` CHECK (estado IN ('ACTIVO','INACTIVO','PENDIENTE')),
                            KEY `idx_clientes_empresa_id_estado` (`empresa_id`, `estado`),
                            KEY `idx_clientes_empresa_id` (`empresa_id`),
                            KEY `idx_clientes_persona_id` (`persona_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Perfil de cliente dentro de una empresa.';

-- Clientes: Proyectos de interés o relación de un cliente.
CREATE TABLE `cliente_proyectos` (
                                     `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                     `cliente_id` BIGINT UNSIGNED NOT NULL,
                                     `proyecto_id` BIGINT UNSIGNED NOT NULL,
                                     `tipo_relacion` VARCHAR(20) NOT NULL,
                                     `es_principal` TINYINT(1) NOT NULL DEFAULT 0,
                                     `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                     PRIMARY KEY (`id`),
                                     CONSTRAINT `uk_cliente_proyectos_cliente_id_proyecto_id_tipo_relacion` UNIQUE (`cliente_id`, `proyecto_id`, `tipo_relacion`),
                                     CONSTRAINT `fk_cliente_proyectos_cliente_id_clientes` FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `fk_cliente_proyectos_proyecto_id_proyectos` FOREIGN KEY (`proyecto_id`) REFERENCES `proyectos` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `ck_cliente_proyectos_1` CHECK (tipo_relacion IN ('INTERESADO','COMPRADOR','PROPIETARIO')),
                                     KEY `idx_cliente_proyectos_cliente_id` (`cliente_id`),
                                     KEY `idx_cliente_proyectos_proyecto_id` (`proyecto_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Proyectos de interés o relación de un cliente.';

-- Seguridad: Sesiones y tokens de actualización revocables.
CREATE TABLE `sesiones_usuario` (
                                    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                    `usuario_id` BIGINT UNSIGNED NOT NULL,
                                    `token_hash` CHAR(64) NOT NULL,
                                    `ip_origen` VARCHAR(64),
                                    `agente_usuario` VARCHAR(500),
                                    `iniciada_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                    `expira_en` DATETIME(6) NOT NULL,
                                    `revocada_en` DATETIME(6),
                                    `motivo_revocacion` VARCHAR(200),
                                    `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                    `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                    PRIMARY KEY (`id`),
                                    CONSTRAINT `uk_sesiones_usuario_token_hash` UNIQUE (`token_hash`),
                                    CONSTRAINT `fk_sesiones_usuario_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
                                    KEY `idx_sesiones_usuario_usuario_id_expira_en` (`usuario_id`, `expira_en`),
                                    KEY `idx_sesiones_usuario_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sesiones y tokens de actualización revocables.';

-- Seguridad: Registro de accesos exitosos y fallidos.
CREATE TABLE `intentos_acceso` (
                                   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                   `usuario_id` BIGINT UNSIGNED,
                                   `identificador` VARCHAR(180) NOT NULL,
                                   `resultado` VARCHAR(20) NOT NULL,
                                   `motivo` VARCHAR(200),
                                   `ip_origen` VARCHAR(64),
                                   `agente_usuario` VARCHAR(500),
                                   `fecha_intento` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                   PRIMARY KEY (`id`),
                                   CONSTRAINT `fk_intentos_acceso_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
                                   CONSTRAINT `ck_intentos_acceso_1` CHECK (resultado IN ('EXITOSO','FALLIDO','BLOQUEADO')),
                                   KEY `idx_intentos_acceso_fecha_intento` (`fecha_intento`),
                                   KEY `idx_intentos_acceso_identificador_fecha_intento` (`identificador`, `fecha_intento`),
                                   KEY `idx_intentos_acceso_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Registro de accesos exitosos y fallidos.';

-- Seguridad: Tokens de recuperación o verificación de cuenta.
CREATE TABLE `tokens_recuperacion` (
                                       `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                       `usuario_id` BIGINT UNSIGNED NOT NULL,
                                       `tipo` VARCHAR(25) NOT NULL,
                                       `token_hash` CHAR(64) NOT NULL,
                                       `expira_en` DATETIME(6) NOT NULL,
                                       `usado_en` DATETIME(6),
                                       `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                       `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                       PRIMARY KEY (`id`),
                                       CONSTRAINT `uk_tokens_recuperacion_token_hash` UNIQUE (`token_hash`),
                                       CONSTRAINT `fk_tokens_recuperacion_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
                                       CONSTRAINT `ck_tokens_recuperacion_1` CHECK (tipo IN ('PASSWORD','CORREO','MFA')),
                                       KEY `idx_tokens_recuperacion_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tokens de recuperación o verificación de cuenta.';

-- Seguridad: Preferencias visuales y de comunicación del usuario.
CREATE TABLE `preferencias_usuario` (
                                        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                        `usuario_id` BIGINT UNSIGNED NOT NULL,
                                        `clave` VARCHAR(100) NOT NULL,
                                        `valor_json` JSON NOT NULL,
                                        `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                        `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                        PRIMARY KEY (`id`),
                                        CONSTRAINT `uk_preferencias_usuario_usuario_id_clave` UNIQUE (`usuario_id`, `clave`),
                                        CONSTRAINT `fk_preferencias_usuario_usuario_id_usuarios` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
                                        KEY `idx_preferencias_usuario_usuario_id` (`usuario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Preferencias visuales y de comunicación del usuario.';
