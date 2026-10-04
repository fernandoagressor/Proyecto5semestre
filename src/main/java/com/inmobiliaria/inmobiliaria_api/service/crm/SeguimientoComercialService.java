package com.inmobiliaria.inmobiliaria_api.service.crm;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ActividadComercialRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ActividadComercialResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CambioEstadoCitaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CitaComercialRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CitaComercialResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.ActividadComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoCitaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ActividadComercialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.CitaComercialRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeguimientoComercialService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final ActividadComercialRepository actividadRepository;
    private final CitaComercialRepository citaRepository;
    private final OportunidadService oportunidadService;
    private final CrmAlcanceService crmAlcanceService;

    public SeguimientoComercialService(
            ActividadComercialRepository actividadRepository,
            CitaComercialRepository citaRepository,
            OportunidadService oportunidadService,
            CrmAlcanceService crmAlcanceService
    ) {
        this.actividadRepository = actividadRepository;
        this.citaRepository = citaRepository;
        this.oportunidadService = oportunidadService;
        this.crmAlcanceService = crmAlcanceService;
    }

    @Transactional(readOnly = true)
    public List<ActividadComercialResponse> listarActividades(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        oportunidadService.obtenerAutorizada(contexto, oportunidadId);
        return actividadRepository
                .findByOportunidadIdOrderByFechaActividadDesc(oportunidadId)
                .stream()
                .map(ActividadComercialResponse::from)
                .toList();
    }

    @Transactional
    public ActividadComercialResponse registrarActividad(
            ContextoUsuario contexto,
            Long oportunidadId,
            ActividadComercialRequest request
    ) {
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        LocalDateTime ahora = ahora();
        if (request.fechaActividad().isAfter(ahora.plusMinutes(5))) {
            throw new ReglaNegocioException(
                    "La actividad no puede registrarse en el futuro"
            );
        }
        if (request.fechaProximaAccion() != null
                && !request.fechaProximaAccion()
                .isAfter(request.fechaActividad())) {
            throw new ReglaNegocioException(
                    "La próxima acción debe ser posterior a la actividad"
            );
        }

        ActividadComercial actividad = new ActividadComercial(
                oportunidadId,
                oportunidad.getAsesorId(),
                request.tipo(),
                normalizar(request.resultado()),
                requerido(request.descripcion()),
                request.fechaActividad(),
                normalizar(request.proximaAccion()),
                request.fechaProximaAccion()
        );
        actividad = actividadRepository.save(actividad);
        oportunidad.registrarActividad(
                request.fechaActividad(),
                request.fechaProximaAccion()
        );
        return ActividadComercialResponse.from(actividad);
    }

    @Transactional(readOnly = true)
    public List<CitaComercialResponse> listarCitas(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        oportunidadService.obtenerAutorizada(contexto, oportunidadId);
        return citaRepository
                .findByOportunidadIdOrderByFechaInicioAsc(oportunidadId)
                .stream()
                .map(CitaComercialResponse::from)
                .toList();
    }

    @Transactional
    public CitaComercialResponse crearCita(
            ContextoUsuario contexto,
            Long oportunidadId,
            CitaComercialRequest request
    ) {
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        validarProgramacion(
                oportunidad.getAsesorId(),
                request.fechaInicio(),
                request.fechaFin(),
                null
        );
        CitaComercial cita = new CitaComercial(
                oportunidadId,
                oportunidad.getAsesorId(),
                requerido(request.tipo()).toUpperCase(),
                request.fechaInicio(),
                request.fechaFin(),
                normalizar(request.lugar())
        );
        return CitaComercialResponse.from(citaRepository.save(cita));
    }

    @Transactional
    public CitaComercialResponse reprogramarCita(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long citaId,
            CitaComercialRequest request
    ) {
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        CitaComercial cita = obtenerCita(oportunidadId, citaId);
        validarProgramacion(
                cita.getAsesorId(),
                request.fechaInicio(),
                request.fechaFin(),
                citaId
        );
        cita.reprogramar(
                request.fechaInicio(),
                request.fechaFin(),
                normalizar(request.lugar())
        );
        return CitaComercialResponse.from(cita);
    }

    @Transactional
    public CitaComercialResponse cambiarEstadoCita(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long citaId,
            CambioEstadoCitaRequest request
    ) {
        oportunidadService.obtenerParaActualizar(contexto, oportunidadId);
        CitaComercial cita = obtenerCita(oportunidadId, citaId);
        cita.cambiarEstado(
                request.estado(),
                normalizar(request.resultado())
        );
        return CitaComercialResponse.from(cita);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CitaComercialResponse> agenda(
            ContextoUsuario contexto,
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoCitaComercial estado,
            Long asesorId,
            int pagina,
            int tamano
    ) {
        validarRangoAgenda(desde, hasta);
        Specification<CitaComercial> filtro = filtroAgenda(contexto);
        filtro = filtro.and((root, query, cb) -> cb.and(
                cb.lessThan(root.get("fechaInicio"), hasta),
                cb.greaterThan(root.get("fechaFin"), desde)
        ));
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        if (asesorId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("asesorId"), asesorId));
        }
        Page<CitaComercial> resultado = citaRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.ASC, "fechaInicio")
                )
        );
        return PaginaResponse.from(resultado, CitaComercialResponse::from);
    }

    private Specification<CitaComercial> filtroAgenda(
            ContextoUsuario contexto
    ) {
        AlcanceProyectos alcance = crmAlcanceService.obtener(contexto);
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Oportunidad> oportunidad = subquery.from(Oportunidad.class);
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(
                    oportunidad.get("empresaId"),
                    contexto.empresaId()
            ));
            if (!alcance.empresaCompleta()) {
                List<Predicate> permitidos = new ArrayList<>();
                if (!alcance.proyectos().isEmpty()) {
                    permitidos.add(oportunidad.get("proyectoId")
                            .in(alcance.proyectos()));
                }
                if (alcance.registrosPropios()) {
                    permitidos.add(cb.equal(
                            oportunidad.get("asesorId"),
                            contexto.usuarioId()
                    ));
                }
                condiciones.add(permitidos.isEmpty()
                        ? cb.disjunction()
                        : cb.or(permitidos.toArray(Predicate[]::new)));
            }
            subquery.select(oportunidad.get("id"))
                    .where(condiciones.toArray(Predicate[]::new));
            return root.get("oportunidadId").in(subquery);
        };
    }

    private CitaComercial obtenerCita(Long oportunidadId, Long citaId) {
        return citaRepository.findByIdAndOportunidadId(citaId, oportunidadId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cita comercial no existe"
                ));
    }

    private void validarProgramacion(
            Long asesorId,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long excluirId
    ) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new ReglaNegocioException(
                    "La fecha final debe ser posterior a la inicial"
            );
        }
        if (!inicio.isAfter(ahora())) {
            throw new ReglaNegocioException(
                    "La cita debe programarse en una fecha futura"
            );
        }
        if (citaRepository.contarCrucesAgenda(
                asesorId,
                inicio,
                fin,
                excluirId
        ) > 0) {
            throw new ConflictoException(
                    "El asesor ya tiene una cita en el horario indicado"
            );
        }
    }

    private static void validarRangoAgenda(
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new ReglaNegocioException(
                    "El rango de agenda es inválido"
            );
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > 93) {
            throw new ReglaNegocioException(
                    "La consulta de agenda no puede superar 93 días"
            );
        }
    }

    private static void exigirAbierta(Oportunidad oportunidad) {
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
    }

    private static String requerido(String valor) {
        String normalizado = normalizar(valor);
        if (normalizado == null) {
            throw new ReglaNegocioException("El valor es obligatorio");
        }
        return normalizado;
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
