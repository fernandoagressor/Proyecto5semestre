package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ReporteDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReporteGenerado;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ReporteGeneradoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de reportes. Desde la rama 7 viven en la colección {@code reportes_generados}
 * de MongoDB; el alcance y los archivos siguen en MySQL.
 */
@Service
public class ReporteService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Set<String> TIPOS = Set.of(
            "INVENTARIO", "EMBUDO_COMERCIAL", "VENTAS", "CARTERA",
            "RECAUDOS", "MORA", "COMISIONES", "METAS", "RENTABILIDAD",
            "PQRS", "TAREAS", "AUDITORIA"
    );
    private final ReporteGeneradoRepository repository;
    private final SecuenciaService secuencias;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final ObjectMapper objectMapper;

    public ReporteService(ReporteGeneradoRepository repository,
                          SecuenciaService secuencias,
                          GerenciaAlcanceService alcanceService,
                          AuditoriaRegistroService auditoria,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.secuencias = secuencias;
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
        ReporteGenerado reporte = repository.insertar(new ReporteGenerado(
                secuencias.siguiente(SecuenciasAuditoria.REPORTES), contexto.empresaId(), request.proyectoId(),
                contexto.usuarioId(), tipo, json(request.filtros()), formato,
                LocalDateTime.now(BOGOTA).plusDays(7)));
        auditoria.registrar(contexto, request.proyectoId(), "REPORTES", "REPORTE_SOLICITAR",
                "REPORTE", reporte.getId(), "Reporte solicitado", null, tipo + ":" + formato);
        return respuesta(reporte);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ReporteDtos.Response> listar(ContextoUsuario contexto, Long proyectoId,
                                                        String tipoReporte, String estado,
                                                        int pagina, int tamano) {
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        String tipo = tipoReporte == null ? null : tipo(tipoReporte);
        String e = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (proyecto != null) condiciones.add(where("proyectoId").is(proyecto));
        if (tipo != null) condiciones.add(where("tipoReporte").is(tipo));
        if (e != null) condiciones.add(where("estado").is(e));
        return PaginaResponse.from(repository.buscar(new Criteria().andOperator(condiciones),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")))),
                ReporteService::respuesta);
    }

    @Transactional(readOnly = true)
    public ReporteDtos.Response obtener(ContextoUsuario contexto, Long id) {
        return respuesta(autorizado(contexto, id));
    }

    @Transactional
    public ReporteDtos.Response completar(ContextoUsuario contexto, Long id,
                                           ReporteDtos.CompletarRequest request) {
        ReporteGenerado reporte = autorizado(contexto, id);
        String anterior = reporte.getEstado();
        if (!ReporteGenerado.PENDIENTE.equals(anterior)) {
            throw new ReglaNegocioException("Solo un reporte pendiente puede completarse");
        }
        boolean exito = request.archivoId() != null;
        if (exito) alcanceService.verificarArchivo(contexto, request.archivoId());
        reporte.completar(request.archivoId(), request.detalleError(), LocalDateTime.now(BOGOTA));
        try {
            reporte = repository.guardar(reporte);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("El reporte fue procesado por otro trabajador");
        }
        auditoria.registrar(contexto, reporte.getProyectoId(), "REPORTES",
                exito ? "REPORTE_GENERAR" : "REPORTE_FALLAR", "REPORTE", id,
                exito ? "Reporte generado" : "Generación de reporte fallida",
                anterior, exito ? request.archivoId() : request.detalleError());
        return respuesta(reporte);
    }

    private ReporteGenerado autorizado(ContextoUsuario contexto, Long id) {
        ReporteGenerado reporte = repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Reporte no encontrado"));
        if (reporte.getProyectoId() != null) alcanceService.verificarProyecto(contexto, reporte.getProyectoId());
        else alcanceService.verificarEmpresa(contexto);
        return reporte;
    }

    private static ReporteDtos.Response respuesta(ReporteGenerado r) {
        return new ReporteDtos.Response(r.getId(), r.getProyectoId(), r.getSolicitadoPor(),
                r.getTipoReporte(), r.getFiltrosJson(), r.getFormato(), r.getArchivoId(), r.getEstado(),
                r.getGeneradoEn(), r.getExpiraEn(), r.getDetalleError(), r.getCreadoEn(),
                r.getVersion() == null ? 0 : r.getVersion());
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
}
