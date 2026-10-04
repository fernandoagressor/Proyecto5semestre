package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioEstadoLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioPrecioLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.HistorialEstadoLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.HistorialPrecioLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.InventarioResumenResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteActualizarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ManzanaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ManzanaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EtapaProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.LoteHistorialEstado;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.LotePrecioHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Manzana;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.EtapaProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteHistorialEstadoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LotePrecioHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ManzanaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final ProyectoService proyectoService;
    private final EtapaProyectoRepository etapaRepository;
    private final ManzanaRepository manzanaRepository;
    private final LoteRepository loteRepository;
    private final LotePrecioHistorialRepository precioRepository;
    private final LoteHistorialEstadoRepository estadoRepository;

    public InventarioService(
            ProyectoService proyectoService,
            EtapaProyectoRepository etapaRepository,
            ManzanaRepository manzanaRepository,
            LoteRepository loteRepository,
            LotePrecioHistorialRepository precioRepository,
            LoteHistorialEstadoRepository estadoRepository
    ) {
        this.proyectoService = proyectoService;
        this.etapaRepository = etapaRepository;
        this.manzanaRepository = manzanaRepository;
        this.loteRepository = loteRepository;
        this.precioRepository = precioRepository;
        this.estadoRepository = estadoRepository;
    }

    @Transactional(readOnly = true)
    public List<EtapaResponse> listarEtapas(
            ContextoUsuario contexto,
            Long proyectoId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        return etapaRepository
                .findByEmpresaIdAndProyectoIdOrderBySecuencia(
                        contexto.empresaId(),
                        proyectoId
                )
                .stream()
                .map(EtapaResponse::from)
                .toList();
    }

    @Transactional
    public EtapaResponse crearEtapa(
            ContextoUsuario contexto,
            Long proyectoId,
            EtapaRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        String codigo = codigo(request.codigo());
        if (etapaRepository.existsByProyectoIdAndCodigoIgnoreCase(
                proyectoId,
                codigo
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con el código indicado"
            );
        }
        if (etapaRepository.existsByProyectoIdAndSecuencia(
                proyectoId,
                request.secuencia()
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con la secuencia indicada"
            );
        }
        EstadoEtapa estado = request.estado() == null
                ? EstadoEtapa.ACTIVA
                : request.estado();
        EtapaProyecto etapa = new EtapaProyecto(
                contexto.empresaId(),
                proyectoId,
                codigo,
                requerido(request.nombre()),
                request.secuencia(),
                request.fechaInicio(),
                request.fechaFin()
        );
        etapa.actualizar(
                codigo,
                requerido(request.nombre()),
                request.secuencia(),
                request.fechaInicio(),
                request.fechaFin(),
                estado
        );
        return EtapaResponse.from(etapaRepository.save(etapa));
    }

    @Transactional
    public EtapaResponse actualizarEtapa(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            EtapaRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(
                contexto,
                proyectoId
        );
        verificarProyectoOperable(proyecto);
        EtapaProyecto etapa = etapa(contexto, proyectoId, etapaId);
        String codigo = codigo(request.codigo());
        if (etapaRepository.existsByProyectoIdAndCodigoIgnoreCaseAndIdNot(
                proyectoId,
                codigo,
                etapaId
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con el código indicado"
            );
        }
        if (etapaRepository.existsByProyectoIdAndSecuenciaAndIdNot(
                proyectoId,
                request.secuencia(),
                etapaId
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con la secuencia indicada"
            );
        }
        etapa.actualizar(
                codigo,
                requerido(request.nombre()),
                request.secuencia(),
                request.fechaInicio(),
                request.fechaFin(),
                request.estado() == null ? etapa.getEstado() : request.estado()
        );
        return EtapaResponse.from(etapa);
    }

    @Transactional(readOnly = true)
    public List<ManzanaResponse> listarManzanas(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        etapa(contexto, proyectoId, etapaId);
        return manzanaRepository
                .findByEmpresaIdAndProyectoIdAndEtapaIdOrderBySecuenciaAscCodigoAsc(
                        contexto.empresaId(),
                        proyectoId,
                        etapaId
                )
                .stream()
                .map(ManzanaResponse::from)
                .toList();
    }

    @Transactional
    public ManzanaResponse crearManzana(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            ManzanaRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        EtapaProyecto etapa = etapa(contexto, proyectoId, etapaId);
        if (etapa.getEstado() != EstadoEtapa.ACTIVA) {
            throw new ReglaNegocioException(
                    "Solo se pueden agregar manzanas a una etapa activa"
            );
        }
        String codigo = codigo(request.codigo());
        if (manzanaRepository.existsByEtapaIdAndCodigoIgnoreCase(
                etapaId,
                codigo
        )) {
            throw new ConflictoException(
                    "Ya existe una manzana con el código indicado"
            );
        }
        Manzana manzana = new Manzana(
                contexto.empresaId(),
                proyectoId,
                etapaId,
                codigo,
                requerido(request.nombre()),
                request.secuencia()
        );
        if (request.estado() != null) {
            manzana.actualizar(
                    codigo,
                    requerido(request.nombre()),
                    request.secuencia(),
                    request.estado()
            );
        }
        return ManzanaResponse.from(manzanaRepository.save(manzana));
    }

    @Transactional
    public ManzanaResponse actualizarManzana(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            Long manzanaId,
            ManzanaRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(
                contexto,
                proyectoId
        );
        verificarProyectoOperable(proyecto);
        etapa(contexto, proyectoId, etapaId);
        Manzana manzana = manzana(contexto, proyectoId, etapaId, manzanaId);
        String codigo = codigo(request.codigo());
        if (manzanaRepository.existsByEtapaIdAndCodigoIgnoreCaseAndIdNot(
                etapaId,
                codigo,
                manzanaId
        )) {
            throw new ConflictoException(
                    "Ya existe una manzana con el código indicado"
            );
        }
        manzana.actualizar(
                codigo,
                requerido(request.nombre()),
                request.secuencia(),
                request.estado() == null ? manzana.getEstado() : request.estado()
        );
        return ManzanaResponse.from(manzana);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<LoteResponse> listarLotes(
            ContextoUsuario contexto,
            Long proyectoId,
            EstadoLote estado,
            Long etapaId,
            Long manzanaId,
            String texto,
            BigDecimal areaMinima,
            BigDecimal areaMaxima,
            BigDecimal valorMinimo,
            BigDecimal valorMaximo,
            int pagina,
            int tamano
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        Specification<Lote> filtro = Specification.where(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("empresaId"), contexto.empresaId()),
                        cb.equal(root.get("proyectoId"), proyectoId)
                )
        );
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        if (etapaId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("etapaId"), etapaId));
        }
        if (manzanaId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("manzanaId"), manzanaId));
        }
        String busqueda = normalizarOpcional(texto);
        if (busqueda != null) {
            String patron = "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("codigo")), patron),
                    cb.like(cb.lower(root.get("numero")), patron),
                    cb.like(cb.lower(root.get("ubicacionReferencia")), patron)
            ));
        }
        if (areaMinima != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.<BigDecimal>get("areaM2"), areaMinima));
        }
        if (areaMaxima != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.<BigDecimal>get("areaM2"), areaMaxima));
        }
        if (valorMinimo != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.<BigDecimal>get("valorLista"), valorMinimo));
        }
        if (valorMaximo != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.<BigDecimal>get("valorLista"), valorMaximo));
        }
        Page<Lote> resultado = loteRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.ASC, "codigo")
                )
        );
        return PaginaResponse.from(resultado, LoteResponse::from);
    }

    @Transactional(readOnly = true)
    public LoteResponse obtenerLote(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        return LoteResponse.from(lote(contexto, proyectoId, loteId));
    }

    @Transactional
    public LoteResponse crearLote(
            ContextoUsuario contexto,
            Long proyectoId,
            LoteCrearRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        EtapaProyecto etapa = etapa(
                contexto,
                proyectoId,
                request.etapaId()
        );
        Manzana manzana = manzana(
                contexto,
                proyectoId,
                request.etapaId(),
                request.manzanaId()
        );
        if (etapa.getEstado() != EstadoEtapa.ACTIVA
                || manzana.getEstado() != EstadoEtapa.ACTIVA) {
            throw new ReglaNegocioException(
                    "La etapa y la manzana deben estar activas"
            );
        }
        if (proyecto.getMaxLotes() != null
                && loteRepository.countByProyectoIdAndEmpresaId(
                        proyectoId,
                        contexto.empresaId()
                ) >= proyecto.getMaxLotes()) {
            throw new ReglaNegocioException(
                    "El proyecto alcanzó el máximo de lotes configurado"
            );
        }
        String codigo = codigo(request.codigo());
        validarIdentidadLote(
                proyectoId,
                request.manzanaId(),
                codigo,
                requerido(request.numero()),
                null
        );
        Lote lote = loteRepository.save(new Lote(
                contexto.empresaId(),
                proyectoId,
                request.etapaId(),
                request.manzanaId(),
                codigo,
                requerido(request.numero()),
                request.areaM2(),
                request.frenteM(),
                request.fondoM(),
                request.valorLista(),
                normalizarOpcional(request.ubicacionReferencia()),
                normalizarOpcional(request.descripcion())
        ));
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        precioRepository.save(new LotePrecioHistorial(
                lote.getId(),
                null,
                lote.getValorLista(),
                ahora,
                "Precio inicial del lote",
                contexto.usuarioId()
        ));
        estadoRepository.save(new LoteHistorialEstado(
                lote.getId(),
                null,
                EstadoLote.DISPONIBLE,
                "CREACION_LOTE",
                lote.getId(),
                "Creación del inventario",
                contexto.usuarioId()
        ));
        return LoteResponse.from(lote);
    }

    @Transactional
    public LoteResponse actualizarLote(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId,
            LoteActualizarRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(
                contexto,
                proyectoId
        );
        verificarProyectoOperable(proyecto);
        Lote lote = loteParaActualizar(contexto, proyectoId, loteId);
        if (lote.getEstado() == EstadoLote.RESERVADO
                || lote.getEstado() == EstadoLote.VENDIDO) {
            throw new ReglaNegocioException(
                    "No se pueden modificar los datos físicos de un lote reservado o vendido"
            );
        }
        String codigo = codigo(request.codigo());
        validarIdentidadLote(
                proyectoId,
                lote.getManzanaId(),
                codigo,
                requerido(request.numero()),
                loteId
        );
        lote.actualizarDatos(
                codigo,
                requerido(request.numero()),
                request.areaM2(),
                request.frenteM(),
                request.fondoM(),
                normalizarOpcional(request.ubicacionReferencia()),
                normalizarOpcional(request.descripcion())
        );
        return LoteResponse.from(lote);
    }

    @Transactional
    public LoteResponse cambiarEstado(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId,
            CambioEstadoLoteRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(
                contexto,
                proyectoId
        );
        verificarProyectoOperable(proyecto);
        Lote lote = loteParaActualizar(contexto, proyectoId, loteId);
        EstadoLote anterior = lote.cambiarEstadoManual(request.estado());
        if (anterior != request.estado()) {
            estadoRepository.save(new LoteHistorialEstado(
                    lote.getId(),
                    anterior,
                    request.estado(),
                    "CAMBIO_MANUAL",
                    null,
                    requerido(request.motivo()),
                    contexto.usuarioId()
            ));
        }
        return LoteResponse.from(lote);
    }

    @Transactional
    public LoteResponse cambiarPrecio(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId,
            CambioPrecioLoteRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(
                contexto,
                proyectoId
        );
        verificarProyectoOperable(proyecto);
        Lote lote = loteParaActualizar(contexto, proyectoId, loteId);
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        BigDecimal anterior = lote.cambiarPrecio(request.valor());
        precioRepository
                .findFirstByLoteIdAndVigenteHastaIsNullOrderByVigenteDesdeDesc(
                        loteId
                )
                .ifPresent(historial -> historial.cerrar(ahora));
        precioRepository.save(new LotePrecioHistorial(
                loteId,
                anterior,
                request.valor(),
                ahora,
                requerido(request.motivo()),
                contexto.usuarioId()
        ));
        return LoteResponse.from(lote);
    }

    @Transactional(readOnly = true)
    public List<HistorialEstadoLoteResponse> historialEstados(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        lote(contexto, proyectoId, loteId);
        return estadoRepository.findByLoteIdOrderByCreadoEnDesc(loteId)
                .stream()
                .map(HistorialEstadoLoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioLoteResponse> historialPrecios(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        lote(contexto, proyectoId, loteId);
        return precioRepository.findByLoteIdOrderByVigenteDesdeDesc(loteId)
                .stream()
                .map(HistorialPrecioLoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventarioResumenResponse resumen(
            ContextoUsuario contexto,
            Long proyectoId
    ) {
        proyectoService.obtenerAutorizado(contexto, proyectoId);
        Map<EstadoLote, Long> cantidades = new EnumMap<>(EstadoLote.class);
        for (Object[] fila : loteRepository.resumirPorEstado(
                contexto.empresaId(),
                proyectoId
        )) {
            cantidades.put(
                    (EstadoLote) fila[0],
                    ((Number) fila[1]).longValue()
            );
        }
        long disponibles = cantidades.getOrDefault(EstadoLote.DISPONIBLE, 0L);
        long reservados = cantidades.getOrDefault(EstadoLote.RESERVADO, 0L);
        long vendidos = cantidades.getOrDefault(EstadoLote.VENDIDO, 0L);
        long bloqueados = cantidades.getOrDefault(EstadoLote.BLOQUEADO, 0L);
        BigDecimal valor = loteRepository.sumarValorDisponible(
                contexto.empresaId(),
                proyectoId
        );
        return new InventarioResumenResponse(
                disponibles + reservados + vendidos + bloqueados,
                disponibles,
                reservados,
                vendidos,
                bloqueados,
                valor == null ? BigDecimal.ZERO : valor
        );
    }

    private EtapaProyecto etapa(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId
    ) {
        return etapaRepository
                .findByIdAndEmpresaIdAndProyectoId(
                        etapaId,
                        contexto.empresaId(),
                        proyectoId
                )
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La etapa no existe dentro del proyecto"
                ));
    }

    private Manzana manzana(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            Long manzanaId
    ) {
        return manzanaRepository
                .findByIdAndEmpresaIdAndProyectoIdAndEtapaId(
                        manzanaId,
                        contexto.empresaId(),
                        proyectoId,
                        etapaId
                )
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La manzana no existe dentro de la etapa"
                ));
    }

    private Lote lote(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId
    ) {
        return loteRepository
                .findByIdAndEmpresaIdAndProyectoId(
                        loteId,
                        contexto.empresaId(),
                        proyectoId
                )
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El lote no existe dentro del proyecto"
                ));
    }

    private Lote loteParaActualizar(
            ContextoUsuario contexto,
            Long proyectoId,
            Long loteId
    ) {
        return loteRepository.findForUpdate(
                        loteId,
                        contexto.empresaId(),
                        proyectoId
                )
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El lote no existe dentro del proyecto"
                ));
    }

    private void validarIdentidadLote(
            Long proyectoId,
            Long manzanaId,
            String codigo,
            String numero,
            Long loteId
    ) {
        boolean codigoUsado = loteId == null
                ? loteRepository.existsByProyectoIdAndCodigoIgnoreCase(
                        proyectoId,
                        codigo
                )
                : loteRepository
                        .existsByProyectoIdAndCodigoIgnoreCaseAndIdNot(
                                proyectoId,
                                codigo,
                                loteId
                        );
        if (codigoUsado) {
            throw new ConflictoException(
                    "Ya existe un lote con el código indicado"
            );
        }
        boolean numeroUsado = loteId == null
                ? loteRepository.existsByManzanaIdAndNumeroIgnoreCase(
                        manzanaId,
                        numero
                )
                : loteRepository
                        .existsByManzanaIdAndNumeroIgnoreCaseAndIdNot(
                                manzanaId,
                                numero,
                                loteId
                        );
        if (numeroUsado) {
            throw new ConflictoException(
                    "Ya existe un lote con ese número dentro de la manzana"
            );
        }
    }

    private void verificarProyectoOperable(Proyecto proyecto) {
        if (proyecto.getEstado() == EstadoProyecto.FINALIZADO
                || proyecto.getEstado() == EstadoProyecto.INACTIVO) {
            throw new ReglaNegocioException(
                    "El proyecto no admite cambios de inventario en su estado actual"
            );
        }
    }

    private String codigo(String valor) {
        return requerido(valor).toUpperCase(Locale.ROOT);
    }

    private String requerido(String valor) {
        return valor.trim();
    }

    private String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
