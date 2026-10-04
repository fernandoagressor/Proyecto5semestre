package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.AuditoriaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaConsultaService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbcTemplate;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final ObjectMapper objectMapper;

    public AuditoriaConsultaService(JdbcTemplate jdbcTemplate,
                                    GerenciaAlcanceService alcanceService,
                                    AuditoriaRegistroService auditoria,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaDtos.EventoResponse> eventos(ContextoUsuario contexto,
                                                                 Long proyectoId, Long actorId,
                                                                 String modulo, String accion,
                                                                 LocalDateTime desde, LocalDateTime hasta,
                                                                 int pagina, int tamano) {
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        validarPeriodo(desde, hasta);
        int size = Math.min(Math.max(tamano, 1), 100);
        int page = Math.max(pagina, 0);
        String base = """
                FROM auditoria_eventos
                WHERE empresa_id = ? AND (? IS NULL OR proyecto_id = ?)
                  AND (? IS NULL OR actor_id = ?)
                  AND (? IS NULL OR modulo = ?)
                  AND (? IS NULL OR accion = ?)
                  AND (? IS NULL OR ocurrido_en >= ?)
                  AND (? IS NULL OR ocurrido_en <= ?)
                """;
        Object[] parametros = {contexto.empresaId(), proyecto, proyecto, actorId, actorId,
                normalizar(modulo), normalizar(modulo), normalizar(accion), normalizar(accion),
                desde, desde, hasta, hasta};
        long total = numero("SELECT COUNT(*) " + base, parametros);
        Object[] paginados = java.util.Arrays.copyOf(parametros, parametros.length + 2);
        paginados[parametros.length] = size;
        paginados[parametros.length + 1] = page * size;
        List<AuditoriaDtos.EventoResponse> contenido = jdbcTemplate.query("""
                SELECT id, empresa_id, proyecto_id, actor_id, modulo, accion, entidad_tipo,
                       entidad_id, descripcion, CAST(datos_anteriores_json AS CHAR) datos_anteriores_json,
                       CAST(datos_nuevos_json AS CHAR) datos_nuevos_json, ip_origen, agente_usuario,
                       correlation_id, ocurrido_en
                """ + base + " ORDER BY ocurrido_en DESC LIMIT ? OFFSET ?", (rs, row) ->
                new AuditoriaDtos.EventoResponse(rs.getLong("id"),
                        rs.getObject("empresa_id", Long.class), rs.getObject("proyecto_id", Long.class),
                        rs.getObject("actor_id", Long.class), rs.getString("modulo"), rs.getString("accion"),
                        rs.getString("entidad_tipo"), rs.getObject("entidad_id", Long.class),
                        rs.getString("descripcion"), rs.getString("datos_anteriores_json"),
                        rs.getString("datos_nuevos_json"), rs.getString("ip_origen"),
                        rs.getString("agente_usuario"), rs.getString("correlation_id"),
                        rs.getTimestamp("ocurrido_en").toLocalDateTime()), paginados);
        return pagina(contenido, page, size, total);
    }

    @Transactional(readOnly = true)
    public AuditoriaDtos.EventoResponse evento(ContextoUsuario contexto, Long id) {
        AuditoriaDtos.EventoResponse evento = jdbcTemplate.query("""
                SELECT id, empresa_id, proyecto_id, actor_id, modulo, accion, entidad_tipo,
                       entidad_id, descripcion, CAST(datos_anteriores_json AS CHAR) datos_anteriores_json,
                       CAST(datos_nuevos_json AS CHAR) datos_nuevos_json, ip_origen, agente_usuario,
                       correlation_id, ocurrido_en
                FROM auditoria_eventos WHERE id = ? AND empresa_id = ?
                """, rs -> rs.next() ? new AuditoriaDtos.EventoResponse(rs.getLong("id"),
                rs.getObject("empresa_id", Long.class), rs.getObject("proyecto_id", Long.class),
                rs.getObject("actor_id", Long.class), rs.getString("modulo"), rs.getString("accion"),
                rs.getString("entidad_tipo"), rs.getObject("entidad_id", Long.class),
                rs.getString("descripcion"), rs.getString("datos_anteriores_json"),
                rs.getString("datos_nuevos_json"), rs.getString("ip_origen"),
                rs.getString("agente_usuario"), rs.getString("correlation_id"),
                rs.getTimestamp("ocurrido_en").toLocalDateTime()) : null, id, contexto.empresaId());
        if (evento == null) throw new RecursoNoEncontradoException("Evento de auditoría no encontrado");
        if (evento.proyectoId() != null) alcanceService.verificarProyecto(contexto, evento.proyectoId());
        else alcanceService.verificarEmpresa(contexto);
        return evento;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaDtos.AlertaResponse> alertas(ContextoUsuario contexto,
                                                                 String estado, String severidad,
                                                                 int pagina, int tamano) {
        int size = Math.min(Math.max(tamano, 1), 100);
        int page = Math.max(pagina, 0);
        String base = """
                FROM alertas_anomalia WHERE empresa_id = ?
                  AND (? IS NULL OR estado = ?) AND (? IS NULL OR severidad = ?)
                """;
        String e = normalizar(estado);
        String s = normalizar(severidad);
        Object[] params = {contexto.empresaId(), e, e, s, s};
        long total = numero("SELECT COUNT(*) " + base, params);
        Object[] paginados = java.util.Arrays.copyOf(params, params.length + 2);
        paginados[params.length] = size;
        paginados[params.length + 1] = page * size;
        List<AuditoriaDtos.AlertaResponse> contenido = jdbcTemplate.query("""
                SELECT id, tipo, severidad, usuario_id, evento_auditoria_id, descripcion,
                       estado, asignado_a, resuelta_en, resolucion, creado_en, version
                """ + base + " ORDER BY creado_en DESC LIMIT ? OFFSET ?", (rs, row) -> alerta(rs), paginados);
        return pagina(contenido, page, size, total);
    }

    @Transactional
    public AuditoriaDtos.AlertaResponse asignar(ContextoUsuario contexto, Long id,
                                                 AuditoriaDtos.AsignarAlertaRequest request) {
        alcanceService.verificarUsuarioEmpresa(contexto, request.usuarioId());
        AuditoriaDtos.AlertaResponse actual = alertaParaActualizar(contexto, id);
        if ("RESUELTA".equals(actual.estado()) || "DESCARTADA".equals(actual.estado())) {
            throw new ReglaNegocioException("La alerta ya fue cerrada");
        }
        int filas = jdbcTemplate.update("""
                UPDATE alertas_anomalia SET asignado_a = ?, estado = 'EN_INVESTIGACION',
                       version = version + 1
                WHERE id = ? AND empresa_id = ? AND version = ?
                """, request.usuarioId(), id, contexto.empresaId(), actual.version());
        if (filas != 1) throw new ConflictoException("La alerta fue modificada por otro usuario");
        auditoria.registrar(contexto, null, "AUDITORIA", "ALERTA_ASIGNAR", "ALERTA_ANOMALIA",
                id, "Alerta asignada", actual.asignadoA(), request.usuarioId());
        return alertaParaActualizar(contexto, id);
    }

    @Transactional
    public AuditoriaDtos.AlertaResponse resolver(ContextoUsuario contexto, Long id,
                                                  AuditoriaDtos.ResolverAlertaRequest request) {
        AuditoriaDtos.AlertaResponse actual = alertaParaActualizar(contexto, id);
        if ("RESUELTA".equals(actual.estado()) || "DESCARTADA".equals(actual.estado())) {
            throw new ReglaNegocioException("La alerta ya fue cerrada");
        }
        String estado = request.descartar() ? "DESCARTADA" : "RESUELTA";
        int filas = jdbcTemplate.update("""
                UPDATE alertas_anomalia SET estado = ?, resuelta_en = ?, resolucion = ?,
                       asignado_a = COALESCE(asignado_a, ?), version = version + 1
                WHERE id = ? AND empresa_id = ? AND version = ?
                """, estado, LocalDateTime.now(BOGOTA), request.resolucion().trim(),
                contexto.usuarioId(), id, contexto.empresaId(), actual.version());
        if (filas != 1) throw new ConflictoException("La alerta fue modificada por otro usuario");
        auditoria.registrar(contexto, null, "AUDITORIA", "ALERTA_CERRAR", "ALERTA_ANOMALIA",
                id, "Alerta " + estado.toLowerCase(Locale.ROOT), actual.estado(), estado);
        return alertaParaActualizar(contexto, id);
    }

    @Transactional
    public AuditoriaDtos.ExportacionResponse solicitarExportacion(
            ContextoUsuario contexto, AuditoriaDtos.ExportarRequest request) {
        String formato = formato(request.formato());
        String filtros = json(request.filtros());
        jdbcTemplate.update("""
                INSERT INTO exportaciones_auditoria
                (empresa_id, usuario_id, filtros_json, formato, motivo, estado, expira_en)
                VALUES (?, ?, CAST(? AS JSON), ?, ?, 'PENDIENTE', ?)
                """, contexto.empresaId(), contexto.usuarioId(), filtros, formato,
                request.motivo().trim(), LocalDateTime.now(BOGOTA).plusDays(7));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditoria.registrar(contexto, null, "AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR",
                "EXPORTACION_AUDITORIA", id, "Exportación de auditoría solicitada", null, formato);
        return exportacion(contexto, id);
    }

    @Transactional(readOnly = true)
    public AuditoriaDtos.ExportacionResponse exportacion(ContextoUsuario contexto, Long id) {
        AuditoriaDtos.ExportacionResponse resultado = jdbcTemplate.query("""
                SELECT id, formato, motivo, estado, archivo_id, expira_en, creado_en
                FROM exportaciones_auditoria WHERE id = ? AND empresa_id = ? AND usuario_id = ?
                """, rs -> rs.next() ? new AuditoriaDtos.ExportacionResponse(rs.getLong("id"),
                rs.getString("formato"), rs.getString("motivo"), rs.getString("estado"),
                rs.getObject("archivo_id", Long.class), timestamp(rs, "expira_en"),
                timestamp(rs, "creado_en")) : null, id, contexto.empresaId(), contexto.usuarioId());
        if (resultado == null) throw new RecursoNoEncontradoException("Exportación no encontrada");
        return resultado;
    }

    private AuditoriaDtos.AlertaResponse alertaParaActualizar(ContextoUsuario contexto, Long id) {
        AuditoriaDtos.AlertaResponse alerta = jdbcTemplate.query("""
                SELECT id, tipo, severidad, usuario_id, evento_auditoria_id, descripcion,
                       estado, asignado_a, resuelta_en, resolucion, creado_en, version
                FROM alertas_anomalia WHERE id = ? AND empresa_id = ? FOR UPDATE
                """, rs -> rs.next() ? alerta(rs) : null, id, contexto.empresaId());
        if (alerta == null) throw new RecursoNoEncontradoException("Alerta no encontrada");
        return alerta;
    }

    private static AuditoriaDtos.AlertaResponse alerta(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditoriaDtos.AlertaResponse(rs.getLong("id"), rs.getString("tipo"),
                rs.getString("severidad"), rs.getObject("usuario_id", Long.class),
                rs.getObject("evento_auditoria_id", Long.class), rs.getString("descripcion"),
                rs.getString("estado"), rs.getObject("asignado_a", Long.class),
                timestamp(rs, "resuelta_en"), rs.getString("resolucion"),
                timestamp(rs, "creado_en"), rs.getLong("version"));
    }

    private long numero(String sql, Object... params) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, params);
        return total == null ? 0 : total;
    }

    private static <T> PaginaResponse<T> pagina(List<T> contenido, int pagina, int tamano, long total) {
        int paginas = tamano == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, total, paginas,
                pagina == 0, paginas == 0 || pagina >= paginas - 1);
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor == null ? java.util.Map.of() : valor);
        } catch (JsonProcessingException ex) {
            throw new ReglaNegocioException("Los filtros no tienen un formato JSON válido");
        }
    }

    private static String formato(String valor) {
        String formato = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(formato)) {
            throw new ReglaNegocioException("El formato debe ser CSV, XLSX o PDF");
        }
        return formato;
    }

    private static void validarPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial");
        }
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime timestamp(java.sql.ResultSet rs, String columna) throws java.sql.SQLException {
        java.sql.Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
