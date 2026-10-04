package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.TareaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PrioridadTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TareaHistorial;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.TareaHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.TareaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TareaService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final TareaRepository repository;
    private final TareaHistorialRepository historialRepository;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;

    public TareaService(TareaRepository repository,
                        TareaHistorialRepository historialRepository,
                        GerenciaAlcanceService alcanceService,
                        AuditoriaRegistroService auditoria) {
        this.repository = repository;
        this.historialRepository = historialRepository;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<TareaDtos.Response> listar(ContextoUsuario contexto, Long proyectoId,
                                                      Long asignadoA, EstadoTarea estado,
                                                      PrioridadTarea prioridad, int pagina, int tamano) {
        if (proyectoId != null) alcanceService.verificarProyecto(contexto, proyectoId);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        Specification<Tarea> filtro = (root, query, cb) -> cb.equal(root.get("empresaId"), contexto.empresaId());
        if (proyectoId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("proyectoId"), proyectoId));
        if (asignadoA != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("asignadoA"), asignadoA));
        if (!alcance.empresaCompleta() && proyectoId == null) {
            filtro = filtro.and((root, query, cb) -> {
                var permitidos = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(root.get("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) {
                    permitidos.add(cb.equal(root.get("asignadoA"), contexto.usuarioId()));
                    permitidos.add(cb.equal(root.get("creadoPor"), contexto.usuarioId()));
                }
                return permitidos.isEmpty() ? cb.disjunction()
                        : cb.or(permitidos.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (prioridad != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("prioridad"), prioridad));
        Page<Tarea> resultado = repository.findAll(filtro, PageRequest.of(Math.max(0, pagina),
                Math.min(Math.max(1, tamano), 100), Sort.by(Sort.Direction.ASC, "fechaVencimiento")));
        return PaginaResponse.from(resultado, TareaDtos.Response::from);
    }

    @Transactional
    public TareaDtos.Response crear(ContextoUsuario contexto, TareaDtos.CrearRequest request) {
        if (request.proyectoId() == null) alcanceService.verificarEmpresa(contexto);
        else alcanceService.verificarProyecto(contexto, request.proyectoId());
        alcanceService.verificarUsuarioEmpresa(contexto, request.asignadoA());
        Tarea tarea = repository.save(new Tarea(contexto.empresaId(), request.proyectoId(),
                request.asignadoA(), contexto.usuarioId(), request.titulo(), request.descripcion(),
                request.tipo().trim().toUpperCase(), request.prioridad(), request.fechaVencimiento(),
                request.entidadTipo(), request.entidadId(), request.reglaOrigen()));
        historialRepository.save(new TareaHistorial(tarea.getId(), null, tarea.getEstado().name(),
                contexto.usuarioId(), "Tarea creada"));
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
        EstadoTarea anterior = tarea.cambiarEstado(request.estado(), LocalDateTime.now(BOGOTA));
        historialRepository.save(new TareaHistorial(tarea.getId(), anterior.name(), tarea.getEstado().name(),
                contexto.usuarioId(), request.comentario()));
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
        historialRepository.save(new TareaHistorial(tarea.getId(), tarea.getEstado().name(),
                tarea.getEstado().name(), contexto.usuarioId(),
                "Reasignada de " + anterior + " a " + request.usuarioId() + ": " + request.motivo()));
        auditoria.registrar(contexto, tarea.getProyectoId(), "TAREAS", "TAREA_REASIGNAR", "TAREA",
                tarea.getId(), "Tarea reasignada", anterior, request.usuarioId());
        return TareaDtos.Response.from(tarea);
    }

    @Transactional(readOnly = true)
    public List<TareaDtos.HistorialResponse> historial(ContextoUsuario contexto, Long id) {
        autorizada(contexto, id);
        return historialRepository.findByTareaIdOrderByCreadoEnAsc(id).stream()
                .map(TareaDtos.HistorialResponse::from).toList();
    }

    @Transactional
    public int procesarVencidas() {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        Specification<Tarea> filtro = (root, query, cb) -> cb.and(
                root.get("estado").in(EstadoTarea.PENDIENTE, EstadoTarea.EN_PROCESO),
                cb.isNotNull(root.get("fechaVencimiento")),
                cb.lessThan(root.get("fechaVencimiento"), ahora));
        List<Tarea> tareas = repository.findAll(filtro);
        tareas.forEach(tarea -> {
            EstadoTarea anterior = tarea.getEstado();
            tarea.marcarVencida(ahora);
            historialRepository.save(new TareaHistorial(tarea.getId(), anterior.name(),
                    EstadoTarea.VENCIDA.name(), null, "Vencimiento automático"));
        });
        return tareas.size();
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
}
