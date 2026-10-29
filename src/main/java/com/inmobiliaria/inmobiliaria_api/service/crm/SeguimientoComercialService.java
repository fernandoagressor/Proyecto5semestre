package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.springframework.data.mongodb.core.query.Criteria.where;

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
import com.inmobiliaria.inmobiliaria_api.repository.crm.CitaComercialRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actividades y citas comerciales. Desde la rama 8 las actividades viven embebidas en la
 * oportunidad y las citas en la colección {@code citas_comerciales} de MongoDB (con copia de la
 * empresa y el proyecto de la oportunidad para filtrar la agenda por alcance).
 */
@Service
public class SeguimientoComercialService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final CitaComercialRepository citaRepository;
    private final OportunidadService oportunidadService;
    private final CrmAlcanceService crmAlcanceService;
    private final SecuenciaService secuencias;

    public SeguimientoComercialService(
            CitaComercialRepository citaRepository,
            OportunidadService oportunidadService,
            CrmAlcanceService crmAlcanceService,
            SecuenciaService secuencias
    ) {
        this.citaRepository = citaRepository;
        this.oportunidadService = oportunidadService;
        this.crmAlcanceService = crmAlcanceService;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public List<ActividadComercialResponse> listarActividades(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        return oportunidadService.obtenerAutorizada(contexto, oportunidadId).getActividades()
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

        ActividadComercial actividad = oportunidad.agregarActividad(
                secuencias.siguiente(SecuenciasCrm.ACTIVIDADES),
                request.tipo(),
                normalizar(request.resultado()),
                requerido(request.descripcion()),
                request.fechaActividad(),
                normalizar(request.proximaAccion()),
                request.fechaProximaAccion()
        );
        oportunidadService.guardar(oportunidad);
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
                oportunidad.getEmpresaId(),
                oportunidad.getProyectoId(),
                oportunidadId,
                oportunidad.getAsesorId(),
                requerido(request.tipo()).toUpperCase(),
                request.fechaInicio(),
                request.fechaFin(),
                normalizar(request.lugar())
        );
        cita.identificar(secuencias.siguiente(SecuenciasCrm.CITAS));
        return CitaComercialResponse.from(citaRepository.insertar(cita));
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
        return CitaComercialResponse.from(guardar(cita));
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
        return CitaComercialResponse.from(guardar(cita));
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
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(crmAlcanceService.filtroMongo(contexto));
        condiciones.add(where("fechaInicio").lt(hasta));
        condiciones.add(where("fechaFin").gt(desde));
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (asesorId != null) condiciones.add(where("asesorId").is(asesorId));
        var resultado = citaRepository.buscar(
                new Criteria().andOperator(condiciones),
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.ASC, "fechaInicio").and(Sort.by(Sort.Direction.ASC, "id"))
                )
        );
        return PaginaResponse.from(resultado, CitaComercialResponse::from);
    }

    private CitaComercial guardar(CitaComercial cita) {
        try {
            return citaRepository.guardar(cita);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("La cita fue modificada por otro usuario");
        }
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
