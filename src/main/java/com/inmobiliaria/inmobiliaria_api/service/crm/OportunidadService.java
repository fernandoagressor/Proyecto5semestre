package com.inmobiliaria.inmobiliaria_api.service.crm;

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
import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadEtapaHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadLote;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadEtapaHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadLoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OportunidadService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final long MAXIMO_LOTES_INTERES = 10;

    private final OportunidadRepository oportunidadRepository;
    private final OportunidadLoteRepository oportunidadLoteRepository;
    private final OportunidadEtapaHistorialRepository historialRepository;
    private final LoteRepository loteRepository;
    private final ProspectoService prospectoService;
    private final CrmAlcanceService crmAlcanceService;

    public OportunidadService(
            OportunidadRepository oportunidadRepository,
            OportunidadLoteRepository oportunidadLoteRepository,
            OportunidadEtapaHistorialRepository historialRepository,
            LoteRepository loteRepository,
            ProspectoService prospectoService,
            CrmAlcanceService crmAlcanceService
    ) {
        this.oportunidadRepository = oportunidadRepository;
        this.oportunidadLoteRepository = oportunidadLoteRepository;
        this.historialRepository = historialRepository;
        this.loteRepository = loteRepository;
        this.prospectoService = prospectoService;
        this.crmAlcanceService = crmAlcanceService;
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
        Specification<Oportunidad> filtro = filtroAlcance(contexto);
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        if (etapa != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("etapaActual"), etapa));
        }
        if (proyectoId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("proyectoId"), proyectoId));
        }
        if (asesorId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("asesorId"), asesorId));
        }
        String busqueda = normalizar(texto);
        if (busqueda != null) {
            String patron = "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("codigo")), patron));
        }

        Page<Oportunidad> resultado = oportunidadRepository.findAll(
                filtro,
                PageRequest.of(
                        paginaValida(pagina),
                        tamanoValido(tamano),
                        Sort.by(Sort.Direction.DESC, "creadoEn")
                )
        );
        return PaginaResponse.from(resultado, OportunidadResponse::from);
    }

    @Transactional(readOnly = true)
    public OportunidadResponse obtener(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        return OportunidadResponse.from(
                obtenerAutorizada(contexto, oportunidadId)
        );
    }

    @Transactional
    public OportunidadResponse crear(
            ContextoUsuario contexto,
            OportunidadRequest request
    ) {
        Prospecto prospecto = prospectoService.obtenerAutorizado(
                contexto,
                request.prospectoId()
        );
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
        if (oportunidadRepository.existsByProspectoIdAndEstado(
                prospecto.getId(),
                EstadoOportunidad.ABIERTA
        )) {
            throw new ConflictoException(
                    "El prospecto ya tiene una oportunidad abierta"
            );
        }

        Long asesorId = crmAlcanceService.validarAsesor(
                contexto,
                request.asesorId() == null
                        ? prospecto.getAsesorId()
                        : request.asesorId()
        );
        crmAlcanceService.verificarRegistro(
                contexto,
                request.proyectoId(),
                asesorId
        );

        Oportunidad oportunidad = new Oportunidad(
                contexto.empresaId(),
                request.proyectoId(),
                generarCodigo("OP"),
                prospecto.getId(),
                asesorId,
                request.valorEstimado(),
                request.fechaProximaAccion()
        );
        oportunidad = oportunidadRepository.save(oportunidad);
        historialRepository.save(new OportunidadEtapaHistorial(
                oportunidad.getId(),
                null,
                oportunidad.getEtapaActual(),
                contexto.usuarioId(),
                "Creación de la oportunidad"
        ));
        return OportunidadResponse.from(oportunidad);
    }

    @Transactional
    public OportunidadResponse actualizar(
            ContextoUsuario contexto,
            Long oportunidadId,
            OportunidadActualizarRequest request
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        oportunidad.actualizarDatos(
                request.valorEstimado(),
                request.fechaProximaAccion()
        );
        return OportunidadResponse.from(oportunidad);
    }

    @Transactional
    public OportunidadResponse cambiarEtapa(
            ContextoUsuario contexto,
            Long oportunidadId,
            CambioEtapaOportunidadRequest request
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        EtapaOportunidad anterior = oportunidad.cambiarEtapa(
                request.etapa(),
                request.probabilidad()
        );
        historialRepository.save(new OportunidadEtapaHistorial(
                oportunidad.getId(),
                anterior,
                oportunidad.getEtapaActual(),
                contexto.usuarioId(),
                normalizar(request.observacion())
        ));
        return OportunidadResponse.from(oportunidad);
    }

    @Transactional
    public OportunidadResponse cerrarPerdida(
            ContextoUsuario contexto,
            Long oportunidadId,
            String motivo
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        oportunidad.cerrarPerdida(requerido(motivo), ahora());
        return OportunidadResponse.from(oportunidad);
    }

    @Transactional
    public OportunidadResponse reabrir(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        oportunidad.reabrir();
        return OportunidadResponse.from(oportunidad);
    }

    @Transactional
    public Oportunidad ganarPorContrato(
            ContextoUsuario contexto,
            Long oportunidadId,
            LocalDateTime fecha,
            Long contratoId
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        EtapaOportunidad anterior = oportunidad.getEtapaActual();
        oportunidad.ganarPorContrato(fecha);
        historialRepository.save(new OportunidadEtapaHistorial(
                oportunidad.getId(),
                anterior,
                oportunidad.getEtapaActual(),
                contexto.usuarioId(),
                "Venta confirmada por contrato " + contratoId
        ));
        return oportunidad;
    }

    @Transactional
    public Oportunidad reabrirPorAnulacionContrato(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long contratoId
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        EtapaOportunidad anterior = oportunidad.getEtapaActual();
        oportunidad.reabrirPorAnulacionContrato();
        historialRepository.save(new OportunidadEtapaHistorial(
                oportunidad.getId(),
                anterior,
                oportunidad.getEtapaActual(),
                contexto.usuarioId(),
                "Venta reabierta por anulación del contrato " + contratoId
        ));
        return oportunidad;
    }

    @Transactional(readOnly = true)
    public List<OportunidadLoteResponse> listarLotes(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        obtenerAutorizada(contexto, oportunidadId);
        return oportunidadLoteRepository
                .findByOportunidadIdOrderByPrioridadAsc(oportunidadId)
                .stream()
                .map(OportunidadLoteResponse::from)
                .toList();
    }

    @Transactional
    public OportunidadLoteResponse agregarLote(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long loteId,
            Integer prioridad
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        if (oportunidadLoteRepository.countByOportunidadId(oportunidadId)
                >= MAXIMO_LOTES_INTERES) {
            throw new ReglaNegocioException(
                    "Una oportunidad no puede tener más de diez lotes de interés"
            );
        }
        if (oportunidadLoteRepository.existsByOportunidadIdAndLoteId(
                oportunidadId,
                loteId
        )) {
            throw new ConflictoException(
                    "El lote ya está vinculado a la oportunidad"
            );
        }
        Lote lote = obtenerLote(oportunidad, loteId);
        if (lote.getEstado() != EstadoLote.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "Solo se pueden vincular lotes disponibles"
            );
        }
        OportunidadLote relacion = new OportunidadLote(
                oportunidadId,
                loteId,
                prioridad == null ? 1 : prioridad
        );
        return OportunidadLoteResponse.from(
                oportunidadLoteRepository.save(relacion)
        );
    }

    @Transactional
    public void quitarLote(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long loteId
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        OportunidadLote relacion = obtenerRelacion(oportunidadId, loteId);
        oportunidadLoteRepository.delete(relacion);
    }

    @Transactional
    public OportunidadLoteResponse seleccionarLote(
            ContextoUsuario contexto,
            Long oportunidadId,
            Long loteId,
            boolean seleccionado
    ) {
        Oportunidad oportunidad = obtenerParaActualizar(
                contexto,
                oportunidadId
        );
        exigirAbierta(oportunidad);
        OportunidadLote objetivo = obtenerRelacion(oportunidadId, loteId);
        obtenerLote(oportunidad, loteId);
        if (seleccionado) {
            oportunidadLoteRepository
                    .findByOportunidadIdOrderByPrioridadAsc(oportunidadId)
                    .forEach(relacion ->
                            relacion.seleccionar(
                                    relacion.getId().equals(objetivo.getId())
                            )
                    );
        } else {
            objetivo.seleccionar(false);
        }
        return OportunidadLoteResponse.from(objetivo);
    }

    @Transactional(readOnly = true)
    public List<HistorialEtapaResponse> historialEtapas(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        obtenerAutorizada(contexto, oportunidadId);
        return historialRepository
                .findByOportunidadIdOrderByCreadoEnAsc(oportunidadId)
                .stream()
                .map(HistorialEtapaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Oportunidad obtenerAutorizada(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        Oportunidad oportunidad = oportunidadRepository
                .findByIdAndEmpresaId(oportunidadId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La oportunidad no existe"
                ));
        crmAlcanceService.verificarRegistro(
                contexto,
                oportunidad.getProyectoId(),
                oportunidad.getAsesorId()
        );
        return oportunidad;
    }

    @Transactional
    public Oportunidad obtenerParaActualizar(
            ContextoUsuario contexto,
            Long oportunidadId
    ) {
        Oportunidad oportunidad = oportunidadRepository
                .findForUpdate(oportunidadId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La oportunidad no existe"
                ));
        crmAlcanceService.verificarRegistro(
                contexto,
                oportunidad.getProyectoId(),
                oportunidad.getAsesorId()
        );
        return oportunidad;
    }

    private Specification<Oportunidad> filtroAlcance(
            ContextoUsuario contexto
    ) {
        AlcanceProyectos alcance = crmAlcanceService.obtener(contexto);
        return (root, query, cb) -> {
            Predicate empresa = cb.equal(
                    root.get("empresaId"),
                    contexto.empresaId()
            );
            if (alcance.empresaCompleta()) {
                return empresa;
            }
            List<Predicate> permitidos = new ArrayList<>();
            if (!alcance.proyectos().isEmpty()) {
                permitidos.add(
                        root.get("proyectoId").in(alcance.proyectos())
                );
            }
            if (alcance.registrosPropios()) {
                permitidos.add(cb.equal(
                        root.get("asesorId"),
                        contexto.usuarioId()
                ));
            }
            Predicate alcanceRegistro = permitidos.isEmpty()
                    ? cb.disjunction()
                    : cb.or(permitidos.toArray(Predicate[]::new));
            return cb.and(empresa, alcanceRegistro);
        };
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

    private OportunidadLote obtenerRelacion(
            Long oportunidadId,
            Long loteId
    ) {
        return oportunidadLoteRepository
                .findByOportunidadIdAndLoteId(oportunidadId, loteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El lote no está vinculado a la oportunidad"
                ));
    }

    private static void exigirAbierta(Oportunidad oportunidad) {
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
    }

    private static String generarCodigo(String prefijo) {
        return prefijo + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
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
