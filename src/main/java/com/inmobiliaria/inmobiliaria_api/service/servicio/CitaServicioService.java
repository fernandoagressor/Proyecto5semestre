package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;

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
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Citas de servicio. Desde la rama 10 viven en la colección {@code citas_servicio} de MongoDB; el
 * dashboard las lee de la tabla espejo {@code lectura_citas_servicio}.
 */
@Service
public class CitaServicioService {
    static final String SECUENCIA = "citas_servicio";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CitaServicioRepository repository;
    private final SolicitudPqrsRepository solicitudRepository;
    private final ServicioClienteAlcanceService alcanceService;
    private final NotificacionService notificacionService;
    private final JdbcTemplate jdbcTemplate;
    private final SecuenciaService secuencias;

    public CitaServicioService(CitaServicioRepository repository,
                               SolicitudPqrsRepository solicitudRepository,
                               ServicioClienteAlcanceService alcanceService,
                               NotificacionService notificacionService,
                               JdbcTemplate jdbcTemplate,
                               SecuenciaService secuencias) {
        this.repository = repository;
        this.solicitudRepository = solicitudRepository;
        this.alcanceService = alcanceService;
        this.notificacionService = notificacionService;
        this.jdbcTemplate = jdbcTemplate;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CitaServicioDtos.Response> listar(ContextoUsuario contexto,
                                                            LocalDateTime desde, LocalDateTime hasta,
                                                            EstadoCitaServicio estado, Long responsableId,
                                                            int pagina, int tamano) {
        validarRango(desde, hasta);
        Cliente cliente = alcanceService.clienteAutenticado(contexto).orElse(null);
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        condiciones.add(where("fechaInicio").lt(hasta));
        condiciones.add(where("fechaFin").gt(desde));
        if (cliente != null) {
            condiciones.add(where("clienteId").is(cliente.getId()));
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            if (!alcance.empresaCompleta()) {
                List<Criteria> permitidos = new ArrayList<>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(where("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) permitidos.add(where("responsableId").is(contexto.usuarioId()));
                condiciones.add(permitidos.isEmpty() ? where("id").in(List.of())
                        : new Criteria().orOperator(permitidos));
            }
        }
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (responsableId != null) condiciones.add(where("responsableId").is(responsableId));
        var resultado = repository.buscar(new Criteria().andOperator(condiciones), PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.ASC, "fechaInicio").and(Sort.by(Sort.Direction.ASC, "id"))));
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
        CitaServicio cita = new CitaServicio(contexto.empresaId(), request.proyectoId(),
                cliente.getId(), request.solicitudId(), responsable, requerido(request.tipo()).toUpperCase(Locale.ROOT),
                request.fechaInicio(), request.fechaFin(), normalizar(request.lugar()));
        cita.identificar(secuencias.siguiente(SECUENCIA));
        cita = repository.insertar(cita);
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
        cita = guardar(cita);
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
        cita = guardar(cita);
        notificar(cita, "CITA_ESTADO", "Estado de cita actualizado");
        return CitaServicioDtos.Response.from(cita);
    }

    private CitaServicio obtener(ContextoUsuario contexto, Long id) {
        CitaServicio cita = repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La cita no existe"));
        alcanceService.validarRelacion(contexto, cita.getClienteId(), cita.getProyectoId(), null);
        return cita;
    }

    private CitaServicio guardar(CitaServicio cita) {
        try {
            return repository.guardar(cita);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictoException("La cita fue modificada por otro usuario");
        }
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
