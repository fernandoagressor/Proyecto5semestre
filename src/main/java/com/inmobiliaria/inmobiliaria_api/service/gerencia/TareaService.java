package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.TareaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PrioridadTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.TareaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tareas de seguimiento. Desde la rama 9 viven en la colección {@code tareas} de MongoDB, con el
 * historial embebido. Las transacciones que quedan son de MySQL (alcance y auditoría, que se
 * escribe al confirmarse).
 */
@Service
public class TareaService {
    static final String SECUENCIA = "tareas";
    static final String SECUENCIA_HISTORIAL = "tarea_historial";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Logger log = LoggerFactory.getLogger(TareaService.class);

    private final TareaRepository repository;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final SecuenciaService secuencias;

    public TareaService(TareaRepository repository,
                        GerenciaAlcanceService alcanceService,
                        AuditoriaRegistroService auditoria,
                        SecuenciaService secuencias) {
        this.repository = repository;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<TareaDtos.Response> listar(ContextoUsuario contexto, Long proyectoId,
                                                      Long asignadoA, EstadoTarea estado,
                                                      PrioridadTarea prioridad, int pagina, int tamano) {
        if (proyectoId != null) alcanceService.verificarProyecto(contexto, proyectoId);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (proyectoId != null) condiciones.add(where("proyectoId").is(proyectoId));
        if (asignadoA != null) condiciones.add(where("asignadoA").is(asignadoA));
        if (!alcance.empresaCompleta() && proyectoId == null) {
            List<Criteria> permitidos = new ArrayList<>();
            if (!alcance.proyectos().isEmpty()) permitidos.add(where("proyectoId").in(alcance.proyectos()));
            if (alcance.registrosPropios()) {
                permitidos.add(where("asignadoA").is(contexto.usuarioId()));
                permitidos.add(where("creadoPor").is(contexto.usuarioId()));
            }
            condiciones.add(permitidos.isEmpty() ? where("id").in(List.of())
                    : new Criteria().orOperator(permitidos));
        }
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (prioridad != null) condiciones.add(where("prioridad").is(prioridad.name()));
        var resultado = repository.buscarResumen(new Criteria().andOperator(condiciones),
                PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamano), 100),
                        Sort.by(Sort.Direction.ASC, "fechaVencimiento").and(Sort.by(Sort.Direction.ASC, "id"))));
        return PaginaResponse.from(resultado, TareaDtos.Response::from);
    }

    @Transactional
    public TareaDtos.Response crear(ContextoUsuario contexto, TareaDtos.CrearRequest request) {
        if (request.proyectoId() == null) alcanceService.verificarEmpresa(contexto);
        else alcanceService.verificarProyecto(contexto, request.proyectoId());
        alcanceService.verificarUsuarioEmpresa(contexto, request.asignadoA());
        Tarea tarea = new Tarea(contexto.empresaId(), request.proyectoId(),
                request.asignadoA(), contexto.usuarioId(), request.titulo(), request.descripcion(),
                request.tipo().trim().toUpperCase(), request.prioridad(), request.fechaVencimiento(),
                request.entidadTipo(), request.entidadId(), request.reglaOrigen());
        tarea.identificar(secuencias.siguiente(SECUENCIA));
        tarea.registrarHistorial(secuencias.siguiente(SECUENCIA_HISTORIAL), null, tarea.getEstado().name(),
                contexto.usuarioId(), "Tarea creada", ahora());
        tarea = repository.insertar(tarea);
        auditoria.registrar(contexto, tarea.getProyectoId(), "TAREAS", "TAREA_CREAR", "TAREA",
                tarea.getId(), "Tarea creada", null, TareaDtos.Response.from(tarea));
        return TareaDtos.Response.from(tarea);
    }

    @Transactional(readOnly = true)
    public TareaDtos.Response obtener(ContextoUsuario contexto, Long id) {
        return TareaDtos.Response.from(autorizada(contexto, id));
    }

    @Transactional
    public TareaDtos.Response cambiarEstado(ContextoUsuario contexto, Long id,
                                             TareaDtos.EstadoRequest request) {
        Tarea tarea = autorizada(contexto, id);
        LocalDateTime ahora = ahora();
        EstadoTarea anterior = tarea.cambiarEstado(request.estado(), ahora);
        tarea.registrarHistorial(secuencias.siguiente(SECUENCIA_HISTORIAL), anterior.name(),
                tarea.getEstado().name(), contexto.usuarioId(), request.comentario(), ahora);
        tarea = guardar(tarea);
        auditoria.registrar(contexto, tarea.getProyectoId(), "TAREAS", "TAREA_ESTADO", "TAREA",
                tarea.getId(), "Estado de tarea actualizado", anterior, tarea.getEstado());
        return TareaDtos.Response.from(tarea);
    }

    @Transactional
    public TareaDtos.Response reasignar(ContextoUsuario contexto, Long id,
                                        TareaDtos.ReasignarRequest request) {
        Tarea tarea = autorizada(contexto, id);
        alcanceService.verificarUsuarioEmpresa(contexto, request.usuarioId());
        Long anterior = tarea.reasignar(request.usuarioId());
        tarea.registrarHistorial(secuencias.siguiente(SECUENCIA_HISTORIAL), tarea.getEstado().name(),
                tarea.getEstado().name(), contexto.usuarioId(),
                "Reasignada de " + anterior + " a " + request.usuarioId() + ": " + request.motivo(), ahora());
        tarea = guardar(tarea);
        auditoria.registrar(contexto, tarea.getProyectoId(), "TAREAS", "TAREA_REASIGNAR", "TAREA",
                tarea.getId(), "Tarea reasignada", anterior, request.usuarioId());
        return TareaDtos.Response.from(tarea);
    }

    @Transactional(readOnly = true)
    public List<TareaDtos.HistorialResponse> historial(ContextoUsuario contexto, Long id) {
        return autorizada(contexto, id).getHistorial().stream()
                .map(TareaDtos.HistorialResponse::from).toList();
    }

    /**
     * Marca VENCIDA cada tarea pendiente o en proceso cuyo plazo ya pasó. Si alguien la cambió
     * mientras tanto (conflicto de versión), se deja para la próxima ejecución.
     */
    public int procesarVencidas() {
        LocalDateTime ahora = ahora();
        int vencidas = 0;
        for (Tarea tarea : repository.buscarPorVencer(ahora)) {
            String anterior = tarea.getEstado().name();
            if (!tarea.marcarVencida(ahora)) {
                continue;
            }
            tarea.registrarHistorial(secuencias.siguiente(SECUENCIA_HISTORIAL), anterior,
                    EstadoTarea.VENCIDA.name(), null, "Vencimiento automático", ahora);
            try {
                repository.guardar(tarea);
                vencidas++;
            } catch (OptimisticLockingFailureException e) {
                log.info("La tarea {} cambió mientras se marcaba vencida; se revisará en la próxima ejecución",
                        tarea.getId());
            }
        }
        return vencidas;
    }

    private Tarea guardar(Tarea tarea) {
        try {
            return repository.guardar(tarea);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictoException("La tarea fue modificada por otro usuario");
        }
    }

    private Tarea autorizada(ContextoUsuario contexto, Long id) {
        Tarea tarea = repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarea no encontrada"));
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        boolean propia = alcance.registrosPropios()
                && (contexto.usuarioId().equals(tarea.getAsignadoA())
                || contexto.usuarioId().equals(tarea.getCreadoPor()));
        boolean porProyecto = tarea.getProyectoId() != null && alcance.proyectos().contains(tarea.getProyectoId());
        if (!alcance.empresaCompleta() && !propia && !porProyecto) {
            throw new AccesoDenegadoException("La tarea no está dentro del alcance del usuario");
        }
        return tarea;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
