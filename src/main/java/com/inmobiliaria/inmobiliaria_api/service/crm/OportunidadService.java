package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CambioEtapaOportunidadRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.HistorialEtapaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadActualizarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadLote;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oportunidades comerciales. Desde la rama 8 viven en la colección {@code oportunidades} de
 * MongoDB, con sus lotes de interés, historial de etapas y actividades embebidos.
 *
 * <p>Cada operación lee el documento, aplica la regla y lo guarda con una sola escritura; si otro
 * usuario lo modificó entretanto, se responde 409. Los lotes y el alcance siguen en MySQL.
 * Los cambios que provoca la venta llegan por {@link PendientesCrmService}.</p>
 */
@Service
public class OportunidadService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final OportunidadRepository oportunidadRepository;
    private final LoteRepository loteRepository;
    private final ProspectoService prospectoService;
    private final CrmAlcanceService crmAlcanceService;
    private final SecuenciaService secuencias;

    public OportunidadService(
            OportunidadRepository oportunidadRepository,
            LoteRepository loteRepository,
            ProspectoService prospectoService,
            CrmAlcanceService crmAlcanceService,
            SecuenciaService secuencias
    ) {
        this.oportunidadRepository = oportunidadRepository;
        this.loteRepository = loteRepository;
        this.prospectoService = prospectoService;
        this.crmAlcanceService = crmAlcanceService;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<OportunidadResponse> listar(
            ContextoUsuario contexto,
            String texto,
            EstadoOportunidad estado,
            EtapaOportunidad etapa,
            Long proyectoId,
            Long asesorId,
            int pagina,
            int tamano
    ) {
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(crmAlcanceService.filtroMongo(contexto));
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (etapa != null) condiciones.add(where("etapaActual").is(etapa.name()));
        if (proyectoId != null) condiciones.add(where("proyectoId").is(proyectoId));
        if (asesorId != null) condiciones.add(where("asesorId").is(asesorId));
        String busqueda = normalizar(texto);
        if (busqueda != null) {
            condiciones.add(where("codigo").regex(Pattern.quote(busqueda), "i"));
        }
        return PaginaResponse.from(oportunidadRepository.buscarResumen(
                new Criteria().andOperator(condiciones),
                PageRequest.of(
                        paginaValida(pagina),
                        tamanoValido(tamano),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))
                )
        ), OportunidadResponse::from);
    }

    @Transactional(readOnly = true)
    public OportunidadResponse obtener(ContextoUsuario contexto, Long oportunidadId) {
        return OportunidadResponse.from(obtenerAutorizada(contexto, oportunidadId));
    }

    @Transactional
    public OportunidadResponse crear(ContextoUsuario contexto, OportunidadRequest request) {
        Prospecto prospecto = prospectoService.obtenerAutorizado(contexto, request.prospectoId());
        if (prospecto.getEstado() != EstadoProspecto.CALIFICADO) {
            throw new ReglaNegocioException(
                    "El prospecto debe estar calificado para crear una oportunidad"
            );
        }
        if (prospecto.getProyectoId() != null
                && !prospecto.getProyectoId().equals(request.proyectoId())) {
            throw new ReglaNegocioException(
                    "El proyecto de la oportunidad no coincide con el prospecto"
            );
        }
        if (oportunidadRepository.existsByProspectoIdAndEstado(prospecto.getId(), EstadoOportunidad.ABIERTA)) {
            throw new ConflictoException("El prospecto ya tiene una oportunidad abierta");
        }

        Long asesorId = crmAlcanceService.validarAsesor(
                contexto,
                request.asesorId() == null ? prospecto.getAsesorId() : request.asesorId()
        );
        crmAlcanceService.verificarRegistro(contexto, request.proyectoId(), asesorId);

        Oportunidad oportunidad = new Oportunidad(
                contexto.empresaId(),
                request.proyectoId(),
                generarCodigo("OP"),
                prospecto.getId(),
                asesorId,
                request.valorEstimado(),
                request.fechaProximaAccion()
        );
        oportunidad.identificar(secuencias.siguiente(SecuenciasCrm.OPORTUNIDADES));
        oportunidad.registrarEtapa(secuencias.siguiente(SecuenciasCrm.HISTORIAL_ETAPAS), null,
                oportunidad.getEtapaActual(), contexto.usuarioId(), "Creación de la oportunidad");
        try {
            return OportunidadResponse.from(oportunidadRepository.insertar(oportunidad));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("No fue posible asignar un código único a la oportunidad");
        }
    }

    @Transactional
    public OportunidadResponse actualizar(ContextoUsuario contexto, Long oportunidadId,
                                          OportunidadActualizarRequest request) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        oportunidad.actualizarDatos(request.valorEstimado(), request.fechaProximaAccion());
        return OportunidadResponse.from(guardar(oportunidad));
    }

    @Transactional
    public OportunidadResponse cambiarEtapa(ContextoUsuario contexto, Long oportunidadId,
                                            CambioEtapaOportunidadRequest request) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        EtapaOportunidad anterior = oportunidad.cambiarEtapa(request.etapa(), request.probabilidad());
        oportunidad.registrarEtapa(secuencias.siguiente(SecuenciasCrm.HISTORIAL_ETAPAS), anterior,
                oportunidad.getEtapaActual(), contexto.usuarioId(), normalizar(request.observacion()));
        return OportunidadResponse.from(guardar(oportunidad));
    }

    @Transactional
    public OportunidadResponse cerrarPerdida(ContextoUsuario contexto, Long oportunidadId, String motivo) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        oportunidad.cerrarPerdida(requerido(motivo), ahora());
        return OportunidadResponse.from(guardar(oportunidad));
    }

    @Transactional
    public OportunidadResponse reabrir(ContextoUsuario contexto, Long oportunidadId) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        oportunidad.reabrir();
        return OportunidadResponse.from(guardar(oportunidad));
    }

    @Transactional(readOnly = true)
    public List<OportunidadLoteResponse> listarLotes(ContextoUsuario contexto, Long oportunidadId) {
        return obtenerAutorizada(contexto, oportunidadId).getLotesInteres().stream()
                .map(OportunidadLoteResponse::from)
                .toList();
    }

    @Transactional
    public OportunidadLoteResponse agregarLote(ContextoUsuario contexto, Long oportunidadId,
                                               Long loteId, Integer prioridad) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        exigirAbierta(oportunidad);
        if (oportunidad.getLotesInteres().size() >= Oportunidad.MAXIMO_LOTES_INTERES) {
            throw new ReglaNegocioException("Una oportunidad no puede tener más de diez lotes de interés");
        }
        if (oportunidad.tieneLote(loteId)) {
            throw new ConflictoException("El lote ya está vinculado a la oportunidad");
        }
        Lote lote = obtenerLote(oportunidad, loteId);
        if (lote.getEstado() != EstadoLote.DISPONIBLE) {
            throw new ReglaNegocioException("Solo se pueden vincular lotes disponibles");
        }
        OportunidadLote relacion = oportunidad.agregarLote(secuencias.siguiente(SecuenciasCrm.LOTES_INTERES),
                loteId, prioridad == null ? 1 : prioridad);
        guardar(oportunidad);
        return OportunidadLoteResponse.from(relacion);
    }

    @Transactional
    public void quitarLote(ContextoUsuario contexto, Long oportunidadId, Long loteId) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        exigirAbierta(oportunidad);
        exigirLote(oportunidad, loteId);
        oportunidad.quitarLote(loteId);
        guardar(oportunidad);
    }

    @Transactional
    public OportunidadLoteResponse seleccionarLote(ContextoUsuario contexto, Long oportunidadId,
                                                   Long loteId, boolean seleccionado) {
        Oportunidad oportunidad = obtenerParaActualizar(contexto, oportunidadId);
        exigirAbierta(oportunidad);
        exigirLote(oportunidad, loteId);
        obtenerLote(oportunidad, loteId);
        OportunidadLote objetivo = oportunidad.seleccionarLote(loteId, seleccionado);
        guardar(oportunidad);
        return OportunidadLoteResponse.from(objetivo);
    }

    @Transactional(readOnly = true)
    public List<HistorialEtapaResponse> historialEtapas(ContextoUsuario contexto, Long oportunidadId) {
        return obtenerAutorizada(contexto, oportunidadId).getHistorialEtapas().stream()
                .map(HistorialEtapaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Oportunidad obtenerAutorizada(ContextoUsuario contexto, Long oportunidadId) {
        Oportunidad oportunidad = oportunidadRepository
                .findByIdAndEmpresaId(oportunidadId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La oportunidad no existe"));
        crmAlcanceService.verificarRegistro(contexto, oportunidad.getProyectoId(), oportunidad.getAsesorId());
        return oportunidad;
    }

    /**
     * Lee la oportunidad para modificarla. Ya no bloquea: la concurrencia la controla la versión
     * del documento al guardar (ver {@link #guardar(Oportunidad)}).
     */
    @Transactional(readOnly = true)
    public Oportunidad obtenerParaActualizar(ContextoUsuario contexto, Long oportunidadId) {
        return obtenerAutorizada(contexto, oportunidadId);
    }

    /** Guarda los cambios; 409 si otro usuario guardó la oportunidad entretanto. */
    public Oportunidad guardar(Oportunidad oportunidad) {
        try {
            return oportunidadRepository.guardar(oportunidad);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("La oportunidad fue modificada por otro usuario");
        }
    }

    /** Borra una oportunidad recién creada por una operación que luego falló (compensación). */
    public void eliminarPorCompensacion(Long oportunidadId) {
        oportunidadRepository.eliminar(oportunidadId);
    }

    private Lote obtenerLote(Oportunidad oportunidad, Long loteId) {
        return loteRepository.findByIdAndEmpresaIdAndProyectoId(
                loteId,
                oportunidad.getEmpresaId(),
                oportunidad.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote no pertenece al proyecto de la oportunidad"
        ));
    }

    private static void exigirLote(Oportunidad oportunidad, Long loteId) {
        if (!oportunidad.tieneLote(loteId)) {
            throw new RecursoNoEncontradoException("El lote no está vinculado a la oportunidad");
        }
    }

    private static void exigirAbierta(Oportunidad oportunidad) {
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException("La oportunidad debe estar abierta");
        }
    }

    private static String generarCodigo(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toUpperCase(Locale.ROOT);
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

    private static int paginaValida(int pagina) {
        return Math.max(pagina, 0);
    }

    private static int tamanoValido(int tamano) {
        return Math.min(Math.max(tamano, 1), 100);
    }
}
