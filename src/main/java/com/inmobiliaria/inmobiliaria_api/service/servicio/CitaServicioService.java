package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.CitaServicioDtos;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CitaServicioRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitaServicioService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CitaServicioRepository repository;
    private final SolicitudPqrsRepository solicitudRepository;
    private final ServicioClienteAlcanceService alcanceService;
    private final NotificacionService notificacionService;
    private final JdbcTemplate jdbcTemplate;

    public CitaServicioService(CitaServicioRepository repository,
                               SolicitudPqrsRepository solicitudRepository,
                               ServicioClienteAlcanceService alcanceService,
                               NotificacionService notificacionService,
                               JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.solicitudRepository = solicitudRepository;
        this.alcanceService = alcanceService;
        this.notificacionService = notificacionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CitaServicioDtos.Response> listar(ContextoUsuario contexto,
                                                            LocalDateTime desde, LocalDateTime hasta,
                                                            EstadoCitaServicio estado, Long responsableId,
                                                            int pagina, int tamano) {
        validarRango(desde, hasta);
        Cliente cliente = alcanceService.clienteAutenticado(contexto).orElse(null);
        Specification<CitaServicio> filtro = (root, query, cb) -> cb.and(
                cb.equal(root.get("empresaId"), contexto.empresaId()),
                cb.lessThan(root.get("fechaInicio"), hasta),
                cb.greaterThan(root.get("fechaFin"), desde));
        if (cliente != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), cliente.getId()));
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            if (!alcance.empresaCompleta()) {
                filtro = filtro.and((root, query, cb) -> {
                    var permitidos = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (!alcance.proyectos().isEmpty()) permitidos.add(root.get("proyectoId").in(alcance.proyectos()));
                    if (alcance.registrosPropios()) permitidos.add(cb.equal(root.get("responsableId"), contexto.usuarioId()));
                    return permitidos.isEmpty() ? cb.disjunction() : cb.or(permitidos.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
            }
        }
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (responsableId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("responsableId"), responsableId));
        Page<CitaServicio> resultado = repository.findAll(filtro, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.ASC, "fechaInicio")));
        return PaginaResponse.from(resultado, CitaServicioDtos.Response::from);
    }

    @Transactional
    public CitaServicioDtos.Response crear(ContextoUsuario contexto, CitaServicioDtos.CrearRequest request) {
        Cliente cliente = alcanceService.validarCliente(contexto, request.clienteId());
        alcanceService.validarRelacion(contexto, cliente.getId(), request.proyectoId(), null);
        if (request.solicitudId() != null) {
            SolicitudPqrs solicitud = solicitudRepository.findByIdAndEmpresaId(request.solicitudId(), contexto.empresaId())
                    .orElseThrow(() -> new ReglaNegocioException("La solicitud relacionada no existe"));
            if (!solicitud.getClienteId().equals(cliente.getId())) {
                throw new ReglaNegocioException("La solicitud no pertenece al cliente");
            }
        }
        if (!request.fechaInicio().isAfter(ahora())) {
            throw new ReglaNegocioException("La cita debe programarse en una fecha futura");
        }
        Long responsable = request.responsableId();
        if (responsable != null) alcanceService.validarUsuarioEmpresa(contexto, responsable);
        validarCruce(responsable, request.fechaInicio(), request.fechaFin(), null);
        CitaServicio cita = repository.save(new CitaServicio(contexto.empresaId(), request.proyectoId(),
                cliente.getId(), request.solicitudId(), responsable, requerido(request.tipo()).toUpperCase(Locale.ROOT),
                request.fechaInicio(), request.fechaFin(), normalizar(request.lugar())));
        notificar(cita, "CITA_CREADA", "Nueva cita de servicio");
        return CitaServicioDtos.Response.from(cita);
    }

    @Transactional
    public CitaServicioDtos.Response reprogramar(ContextoUsuario contexto, Long id,
                                                 CitaServicioDtos.ReprogramarRequest request) {
        CitaServicio cita = obtener(contexto, id);
        if (!request.fechaInicio().isAfter(ahora())) {
            throw new ReglaNegocioException("La cita debe reprogramarse a una fecha futura");
        }
        validarCruce(cita.getResponsableId(), request.fechaInicio(), request.fechaFin(), id);
        cita.reprogramar(request.fechaInicio(), request.fechaFin(), normalizar(request.lugar()));
        notificar(cita, "CITA_REPROGRAMADA", "Cita reprogramada");
        return CitaServicioDtos.Response.from(cita);
    }

    @Transactional
    public CitaServicioDtos.Response cambiarEstado(ContextoUsuario contexto, Long id,
                                                   CitaServicioDtos.EstadoRequest request) {
        CitaServicio cita = obtener(contexto, id);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        if (cliente && request.estado() != EstadoCitaServicio.CANCELADA) {
            throw new AccesoDenegadoException("El cliente solo puede cancelar o reprogramar sus citas");
        }
        switch (request.estado()) {
            case CONFIRMADA -> cita.confirmar();
            case REALIZADA -> cita.completar(request.resultado());
            case CANCELADA -> cita.cancelar(request.resultado());
            default -> throw new ReglaNegocioException("Use la operación de reprogramación para cambiar la fecha");
        }
        notificar(cita, "CITA_ESTADO", "Estado de cita actualizado");
        return CitaServicioDtos.Response.from(cita);
    }

    private CitaServicio obtener(ContextoUsuario contexto, Long id) {
        CitaServicio cita = repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La cita no existe"));
        alcanceService.validarRelacion(contexto, cita.getClienteId(), cita.getProyectoId(), null);
        return cita;
    }

    private void validarCruce(Long responsable, LocalDateTime inicio, LocalDateTime fin, Long excluirId) {
        if (fin == null || inicio == null || !fin.isAfter(inicio)) {
            throw new ReglaNegocioException("El rango de la cita es inválido");
        }
        if (responsable != null && repository.existeCruce(responsable, inicio, fin, excluirId)) {
            throw new ConflictoException("El responsable ya tiene una cita en ese horario");
        }
    }

    private void notificar(CitaServicio cita, String tipo, String titulo) {
        Long usuarioCliente = jdbcTemplate.query("SELECT usuario_id FROM clientes WHERE id = ?",
                rs -> rs.next() ? rs.getObject(1, Long.class) : null, cita.getClienteId());
        notificacionService.crear(cita.getEmpresaId(), usuarioCliente, tipo, titulo,
                cita.getTipo() + " - " + cita.getFechaInicio(), "CITA_SERVICIO", cita.getId(), "MEDIA");
        notificacionService.crear(cita.getEmpresaId(), cita.getResponsableId(), tipo, titulo,
                cita.getTipo() + " - " + cita.getFechaInicio(), "CITA_SERVICIO", cita.getId(), "MEDIA");
    }

    private static void validarRango(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new ReglaNegocioException("El rango de consulta es inválido");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > 186) {
            throw new ReglaNegocioException("La consulta no puede superar 186 días");
        }
    }

    private static String requerido(String valor) {
        String n = normalizar(valor);
        if (n == null) throw new ReglaNegocioException("El valor es obligatorio");
        return n;
    }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private static LocalDateTime ahora() { return LocalDateTime.now(BOGOTA); }
}
