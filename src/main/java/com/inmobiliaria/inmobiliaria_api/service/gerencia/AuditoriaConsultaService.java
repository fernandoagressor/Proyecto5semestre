package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.AuditoriaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ExportacionAuditoria;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.AlertaAnomaliaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ExportacionAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de la bitácora, gestión de alertas y solicitudes de exportación. Desde la rama 7 los
 * datos viven en MongoDB ({@code auditoria_eventos}, {@code alertas_anomalia},
 * {@code exportaciones_auditoria}); el alcance sigue validándose contra MySQL.
 */
@Service
public class AuditoriaConsultaService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final EventoAuditoriaRepository eventos;
    private final AlertaAnomaliaRepository alertas;
    private final ExportacionAuditoriaRepository exportaciones;
    private final SecuenciaService secuencias;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final ObjectMapper objectMapper;

    public AuditoriaConsultaService(EventoAuditoriaRepository eventos,
                                    AlertaAnomaliaRepository alertas,
                                    ExportacionAuditoriaRepository exportaciones,
                                    SecuenciaService secuencias,
                                    GerenciaAlcanceService alcanceService,
                                    AuditoriaRegistroService auditoria,
                                    ObjectMapper objectMapper) {
        this.eventos = eventos;
        this.alertas = alertas;
        this.exportaciones = exportaciones;
        this.secuencias = secuencias;
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
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (proyecto != null) condiciones.add(where("proyectoId").is(proyecto));
        if (actorId != null) condiciones.add(where("actorId").is(actorId));
        if (normalizar(modulo) != null) condiciones.add(where("modulo").is(normalizar(modulo)));
        if (normalizar(accion) != null) condiciones.add(where("accion").is(normalizar(accion)));
        if (desde != null) condiciones.add(where("ocurridoEn").gte(desde));
        if (hasta != null) condiciones.add(where("ocurridoEn").lte(hasta));
        return PaginaResponse.from(eventos.buscar(new Criteria().andOperator(condiciones),
                pagina(pagina, tamano, "ocurridoEn")), AuditoriaConsultaService::respuestaEvento);
    }

    @Transactional(readOnly = true)
    public AuditoriaDtos.EventoResponse evento(ContextoUsuario contexto, Long id) {
        EventoAuditoria evento = eventos.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento de auditoría no encontrado"));
        if (evento.getProyectoId() != null) alcanceService.verificarProyecto(contexto, evento.getProyectoId());
        else alcanceService.verificarEmpresa(contexto);
        return respuestaEvento(evento);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaDtos.AlertaResponse> alertas(ContextoUsuario contexto,
                                                                 String estado, String severidad,
                                                                 int pagina, int tamano) {
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (normalizar(estado) != null) condiciones.add(where("estado").is(normalizar(estado)));
        if (normalizar(severidad) != null) condiciones.add(where("severidad").is(normalizar(severidad)));
        return PaginaResponse.from(alertas.buscar(new Criteria().andOperator(condiciones),
                pagina(pagina, tamano, "creadoEn")), AuditoriaConsultaService::respuestaAlerta);
    }

    @Transactional
    public AuditoriaDtos.AlertaResponse asignar(ContextoUsuario contexto, Long id,
                                                 AuditoriaDtos.AsignarAlertaRequest request) {
        alcanceService.verificarUsuarioEmpresa(contexto, request.usuarioId());
        AlertaAnomalia alerta = alerta(contexto, id);
        Long anterior = alerta.asignar(request.usuarioId());
        alerta = guardar(alerta);
        auditoria.registrar(contexto, null, "AUDITORIA", "ALERTA_ASIGNAR", "ALERTA_ANOMALIA",
                id, "Alerta asignada", anterior, request.usuarioId());
        return respuestaAlerta(alerta);
    }

    @Transactional
    public AuditoriaDtos.AlertaResponse resolver(ContextoUsuario contexto, Long id,
                                                  AuditoriaDtos.ResolverAlertaRequest request) {
        AlertaAnomalia alerta = alerta(contexto, id);
        String anterior = alerta.cerrar(request.resolucion().trim(), request.descartar(),
                contexto.usuarioId(), LocalDateTime.now(BOGOTA));
        alerta = guardar(alerta);
        auditoria.registrar(contexto, null, "AUDITORIA", "ALERTA_CERRAR", "ALERTA_ANOMALIA",
                id, "Alerta " + alerta.getEstado().toLowerCase(Locale.ROOT), anterior, alerta.getEstado());
        return respuestaAlerta(alerta);
    }

    @Transactional
    public AuditoriaDtos.ExportacionResponse solicitarExportacion(
            ContextoUsuario contexto, AuditoriaDtos.ExportarRequest request) {
        String formato = formato(request.formato());
        ExportacionAuditoria exportacion = exportaciones.insertar(new ExportacionAuditoria(
                secuencias.siguiente(SecuenciasAuditoria.EXPORTACIONES), contexto.empresaId(),
                contexto.usuarioId(), json(request.filtros()), formato, request.motivo().trim(),
                LocalDateTime.now(BOGOTA).plusDays(7)));
        auditoria.registrar(contexto, null, "AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR",
                "EXPORTACION_AUDITORIA", exportacion.getId(), "Exportación de auditoría solicitada", null, formato);
        return respuestaExportacion(exportacion);
    }

    @Transactional(readOnly = true)
    public AuditoriaDtos.ExportacionResponse exportacion(ContextoUsuario contexto, Long id) {
        return exportaciones.findDelUsuario(id, contexto.empresaId(), contexto.usuarioId())
                .map(AuditoriaConsultaService::respuestaExportacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("Exportación no encontrada"));
    }

    private AlertaAnomalia alerta(ContextoUsuario contexto, Long id) {
        return alertas.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Alerta no encontrada"));
    }

    private AlertaAnomalia guardar(AlertaAnomalia alerta) {
        try {
            return alertas.guardar(alerta);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("La alerta fue modificada por otro usuario");
        }
    }

    private static AuditoriaDtos.EventoResponse respuestaEvento(EventoAuditoria e) {
        return new AuditoriaDtos.EventoResponse(e.getId(), e.getEmpresaId(), e.getProyectoId(),
                e.getActorId(), e.getModulo(), e.getAccion(), e.getEntidadTipo(), e.getEntidadId(),
                e.getDescripcion(), e.getDatosAnterioresJson(), e.getDatosNuevosJson(), e.getIpOrigen(),
                e.getAgenteUsuario(), e.getCorrelationId(), e.getOcurridoEn());
    }

    private static AuditoriaDtos.AlertaResponse respuestaAlerta(AlertaAnomalia a) {
        return new AuditoriaDtos.AlertaResponse(a.getId(), a.getTipo(), a.getSeveridad(), a.getUsuarioId(),
                a.getEventoAuditoriaId(), a.getDescripcion(), a.getEstado(), a.getAsignadoA(),
                a.getResueltaEn(), a.getResolucion(), a.getCreadoEn(),
                a.getVersion() == null ? 0 : a.getVersion());
    }

    private static AuditoriaDtos.ExportacionResponse respuestaExportacion(ExportacionAuditoria e) {
        return new AuditoriaDtos.ExportacionResponse(e.getId(), e.getFormato(), e.getMotivo(),
                e.getEstado(), e.getArchivoId(), e.getExpiraEn(), e.getCreadoEn());
    }

    private static PageRequest pagina(int pagina, int tamano, String campoFecha) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, campoFecha).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor == null ? Map.of() : valor);
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
}
