package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ReporteDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Set<String> TIPOS = Set.of(
            "INVENTARIO", "EMBUDO_COMERCIAL", "VENTAS", "CARTERA",
            "RECAUDOS", "MORA", "COMISIONES", "METAS", "RENTABILIDAD",
            "PQRS", "TAREAS", "AUDITORIA"
    );
    private final JdbcTemplate jdbcTemplate;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final ObjectMapper objectMapper;

    public ReporteService(JdbcTemplate jdbcTemplate,
                          GerenciaAlcanceService alcanceService,
                          AuditoriaRegistroService auditoria,
                          ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReporteDtos.Response solicitar(ContextoUsuario contexto,
                                           ReporteDtos.SolicitarRequest request) {
        if (request.proyectoId() == null) alcanceService.verificarEmpresa(contexto);
        else alcanceService.verificarProyecto(contexto, request.proyectoId());
        String tipo = tipo(request.tipoReporte());
        String formato = formato(request.formato());
        String filtros = json(request.filtros());
        jdbcTemplate.update("""
                INSERT INTO reportes_generados
                (empresa_id, proyecto_id, solicitado_por, tipo_reporte, filtros_json,
                 formato, estado, expira_en, version)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, 'PENDIENTE', ?, 0)
                """, contexto.empresaId(), request.proyectoId(), contexto.usuarioId(), tipo,
                filtros, formato, LocalDateTime.now(BOGOTA).plusDays(7));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditoria.registrar(contexto, request.proyectoId(), "REPORTES", "REPORTE_SOLICITAR",
                "REPORTE", id, "Reporte solicitado", null, tipo + ":" + formato);
        return obtener(contexto, id);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ReporteDtos.Response> listar(ContextoUsuario contexto, Long proyectoId,
                                                        String tipoReporte, String estado,
                                                        int pagina, int tamano) {
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        int size = Math.min(Math.max(tamano, 1), 100);
        int page = Math.max(pagina, 0);
        String tipo = tipoReporte == null ? null : tipo(tipoReporte);
        String e = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        String base = """
                FROM reportes_generados WHERE empresa_id = ?
                  AND (? IS NULL OR proyecto_id = ?)
                  AND (? IS NULL OR tipo_reporte = ?)
                  AND (? IS NULL OR estado = ?)
                """;
        Object[] params = {contexto.empresaId(), proyecto, proyecto, tipo, tipo, e, e};
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + base, Long.class, params);
        Object[] paginados = java.util.Arrays.copyOf(params, params.length + 2);
        paginados[params.length] = size;
        paginados[params.length + 1] = page * size;
        List<ReporteDtos.Response> contenido = jdbcTemplate.query("""
                SELECT id, proyecto_id, solicitado_por, tipo_reporte,
                       CAST(filtros_json AS CHAR) filtros_json, formato, archivo_id,
                       estado, generado_en, expira_en, detalle_error, creado_en, version
                """ + base + " ORDER BY creado_en DESC LIMIT ? OFFSET ?",
                (rs, row) -> respuesta(rs), paginados);
        long elementos = total == null ? 0 : total;
        int paginas = (int) Math.ceil((double) elementos / size);
        return new PaginaResponse<>(contenido, page, size, elementos, paginas, page == 0,
                paginas == 0 || page >= paginas - 1);
    }

    @Transactional(readOnly = true)
    public ReporteDtos.Response obtener(ContextoUsuario contexto, Long id) {
        ReporteDtos.Response reporte = jdbcTemplate.query("""
                SELECT id, proyecto_id, solicitado_por, tipo_reporte,
                       CAST(filtros_json AS CHAR) filtros_json, formato, archivo_id,
                       estado, generado_en, expira_en, detalle_error, creado_en, version
                FROM reportes_generados WHERE id = ? AND empresa_id = ?
                """, rs -> rs.next() ? respuesta(rs) : null, id, contexto.empresaId());
        if (reporte == null) throw new RecursoNoEncontradoException("Reporte no encontrado");
        if (reporte.proyectoId() != null) alcanceService.verificarProyecto(contexto, reporte.proyectoId());
        else alcanceService.verificarEmpresa(contexto);
        return reporte;
    }

    @Transactional
    public ReporteDtos.Response completar(ContextoUsuario contexto, Long id,
                                           ReporteDtos.CompletarRequest request) {
        ReporteDtos.Response actual = obtenerParaActualizar(contexto, id);
        if (!"PENDIENTE".equals(actual.estado())) {
            throw new ReglaNegocioException("Solo un reporte pendiente puede completarse");
        }
        boolean exito = request.archivoId() != null;
        if (exito) alcanceService.verificarArchivo(contexto, request.archivoId());
        if (!exito && (request.detalleError() == null || request.detalleError().isBlank())) {
            throw new ReglaNegocioException("Un reporte fallido requiere el detalle del error");
        }
        int filas = jdbcTemplate.update("""
                UPDATE reportes_generados
                SET archivo_id = ?, estado = ?, generado_en = ?, detalle_error = ?,
                    version = version + 1
                WHERE id = ? AND empresa_id = ? AND version = ? AND estado = 'PENDIENTE'
                """, request.archivoId(), exito ? "GENERADO" : "FALLIDO",
                LocalDateTime.now(BOGOTA), exito ? null : request.detalleError().trim(),
                id, contexto.empresaId(), actual.version());
        if (filas != 1) throw new ConflictoException("El reporte fue procesado por otro trabajador");
        auditoria.registrar(contexto, actual.proyectoId(), "REPORTES",
                exito ? "REPORTE_GENERAR" : "REPORTE_FALLAR", "REPORTE", id,
                exito ? "Reporte generado" : "Generación de reporte fallida",
                actual.estado(), exito ? request.archivoId() : request.detalleError());
        return obtener(contexto, id);
    }

    private ReporteDtos.Response obtenerParaActualizar(ContextoUsuario contexto, Long id) {
        ReporteDtos.Response reporte = jdbcTemplate.query("""
                SELECT id, proyecto_id, solicitado_por, tipo_reporte,
                       CAST(filtros_json AS CHAR) filtros_json, formato, archivo_id,
                       estado, generado_en, expira_en, detalle_error, creado_en, version
                FROM reportes_generados WHERE id = ? AND empresa_id = ? FOR UPDATE
                """, rs -> rs.next() ? respuesta(rs) : null, id, contexto.empresaId());
        if (reporte == null) throw new RecursoNoEncontradoException("Reporte no encontrado");
        if (reporte.proyectoId() != null) alcanceService.verificarProyecto(contexto, reporte.proyectoId());
        else alcanceService.verificarEmpresa(contexto);
        return reporte;
    }

    private static ReporteDtos.Response respuesta(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReporteDtos.Response(rs.getLong("id"), rs.getObject("proyecto_id", Long.class),
                rs.getObject("solicitado_por", Long.class), rs.getString("tipo_reporte"),
                rs.getString("filtros_json"), rs.getString("formato"),
                rs.getObject("archivo_id", Long.class), rs.getString("estado"),
                timestamp(rs, "generado_en"), timestamp(rs, "expira_en"),
                rs.getString("detalle_error"), timestamp(rs, "creado_en"), rs.getLong("version"));
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor == null ? java.util.Map.of() : valor);
        } catch (JsonProcessingException ex) {
            throw new ReglaNegocioException("Los filtros no tienen un formato JSON válido");
        }
    }

    private static String tipo(String valor) {
        String tipo = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw new ReglaNegocioException("Tipo de reporte no soportado");
        return tipo;
    }

    private static String formato(String valor) {
        String formato = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(formato)) {
            throw new ReglaNegocioException("El formato debe ser CSV, XLSX o PDF");
        }
        return formato;
    }

    private static LocalDateTime timestamp(java.sql.ResultSet rs, String columna) throws java.sql.SQLException {
        java.sql.Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
