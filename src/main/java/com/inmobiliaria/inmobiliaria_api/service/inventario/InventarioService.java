package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioEstadoLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioPrecioLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.GenerarRequest;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final InventarioAvisos avisos;

    public InventarioService(
            ProyectoService proyectoService,
            EtapaProyectoRepository etapaRepository,
            ManzanaRepository manzanaRepository,
            LoteRepository loteRepository,
            LotePrecioHistorialRepository precioRepository,
            LoteHistorialEstadoRepository estadoRepository,
            InventarioAvisos avisos
    ) {
        this.proyectoService = proyectoService;
        this.etapaRepository = etapaRepository;
        this.manzanaRepository = manzanaRepository;
        this.loteRepository = loteRepository;
        this.precioRepository = precioRepository;
        this.estadoRepository = estadoRepository;
        this.avisos = avisos;
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

    /**
     * Crea la etapa con código y secuencia automáticos si no llegan (E01, E02…) y, si se pide,
     * sus manzanas de una vez (M01…Mnn), todo en la misma transacción.
     */
    @Transactional
    public EtapaResponse crearEtapa(
            ContextoUsuario contexto,
            Long proyectoId,
            EtapaRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        int secuencia = request.secuencia() != null
                ? request.secuencia()
                : etapaRepository.maxSecuencia(proyectoId) + 1;
        if (etapaRepository.existsByProyectoIdAndSecuencia(proyectoId, secuencia)) {
            throw new ConflictoException("Ya existe una etapa con la secuencia indicada");
        }
        String codigo = vacio(request.codigo())
                ? codigoEtapaLibre(proyectoId, secuencia)
                : codigo(request.codigo());
        if (etapaRepository.existsByProyectoIdAndCodigoIgnoreCase(proyectoId, codigo)) {
            throw new ConflictoException("Ya existe una etapa con el código " + codigo);
        }
        EstadoEtapa estado = request.estado() == null
                ? EstadoEtapa.ACTIVA
                : request.estado();
        EtapaProyecto etapa = new EtapaProyecto(
                contexto.empresaId(),
                proyectoId,
                codigo,
                requerido(request.nombre()),
                secuencia,
                request.fechaInicio(),
                request.fechaFin()
        );
        etapa.actualizar(
                codigo,
                requerido(request.nombre()),
                secuencia,
                request.fechaInicio(),
                request.fechaFin(),
                estado
        );
        etapa = etapaRepository.save(etapa);
        int cantidad = request.cantidadManzanas() == null ? 0 : request.cantidadManzanas();
        List<Manzana> manzanas = cantidad > 0
                ? crearManzanasAutomaticas(contexto, etapa, cantidad)
                : List.of();
        avisos.registrar(contexto, proyecto, "ETAPA_CREADA", "ETAPA", etapa.getId(),
                "Etapa " + etapa.getNombre() + " creada",
                "creó la etapa " + etapa.getNombre() + " (" + codigo + ")"
                        + (manzanas.isEmpty() ? "" : " con " + manzanas.size() + " manzanas " + rango(manzanas)),
                null, datosEtapa(etapa));
        return EtapaResponse.from(etapa);
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
        Map<String, Object> antes = datosEtapa(etapa);
        String codigo = vacio(request.codigo()) ? etapa.getCodigo() : codigo(request.codigo());
        int secuencia = request.secuencia() == null ? etapa.getSecuencia() : request.secuencia();
        if (etapaRepository.existsByProyectoIdAndCodigoIgnoreCaseAndIdNot(
                proyectoId,
                codigo,
                etapaId
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con el código " + codigo
            );
        }
        if (etapaRepository.existsByProyectoIdAndSecuenciaAndIdNot(
                proyectoId,
                secuencia,
                etapaId
        )) {
            throw new ConflictoException(
                    "Ya existe una etapa con la secuencia indicada"
            );
        }
        etapa.actualizar(
                codigo,
                requerido(request.nombre()),
                secuencia,
                request.fechaInicio(),
                request.fechaFin(),
                request.estado() == null ? etapa.getEstado() : request.estado()
        );
        Map<String, Object> despues = datosEtapa(etapa);
        if (!antes.equals(despues)) {
            avisos.registrar(contexto, proyecto, "ETAPA_EDITADA", "ETAPA", etapa.getId(),
                    "Etapa " + etapa.getNombre() + " actualizada",
                    "actualizó la etapa " + etapa.getNombre() + " (" + cambios(antes, despues) + ")",
                    antes, despues);
        }
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
        int secuencia = request.secuencia() != null
                ? request.secuencia()
                : manzanaRepository.maxSecuencia(etapaId) + 1;
        String codigo = vacio(request.codigo())
                ? siguienteCodigo("M", secuencia, new HashSet<>(manzanaRepository.codigosDeEtapa(etapaId)))
                : codigo(request.codigo());
        if (manzanaRepository.existsByEtapaIdAndCodigoIgnoreCase(
                etapaId,
                codigo
        )) {
            throw new ConflictoException(
                    "Ya existe una manzana con el código " + codigo + " en esta etapa"
            );
        }
        Manzana manzana = new Manzana(
                contexto.empresaId(),
                proyectoId,
                etapaId,
                codigo,
                requerido(request.nombre()),
                secuencia
        );
        if (request.estado() != null) {
            manzana.actualizar(
                    codigo,
                    requerido(request.nombre()),
                    secuencia,
                    request.estado()
            );
        }
        manzana = manzanaRepository.save(manzana);
        avisos.registrar(contexto, proyecto, "MANZANA_CREADA", "MANZANA", manzana.getId(),
                manzana.getNombre() + " creada",
                "creó la manzana " + manzana.getNombre() + " (" + codigo + ") en la etapa " + etapa.getNombre(),
                null, datosManzana(manzana));
        return ManzanaResponse.from(manzana);
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
        Map<String, Object> antes = datosManzana(manzana);
        String codigo = vacio(request.codigo()) ? manzana.getCodigo() : codigo(request.codigo());
        int secuencia = request.secuencia() == null ? manzana.getSecuencia() : request.secuencia();
        if (manzanaRepository.existsByEtapaIdAndCodigoIgnoreCaseAndIdNot(
                etapaId,
                codigo,
                manzanaId
        )) {
            throw new ConflictoException(
                    "Ya existe una manzana con el código " + codigo + " en esta etapa"
            );
        }
        manzana.actualizar(
                codigo,
                requerido(request.nombre()),
                secuencia,
                request.estado() == null ? manzana.getEstado() : request.estado()
        );
        Map<String, Object> despues = datosManzana(manzana);
        if (!antes.equals(despues)) {
            avisos.registrar(contexto, proyecto, "MANZANA_EDITADA", "MANZANA", manzana.getId(),
                    manzana.getNombre() + " actualizada",
                    "actualizó la manzana " + manzana.getNombre() + " (" + cambios(antes, despues) + ")",
                    antes, despues);
        }
        return ManzanaResponse.from(manzana);
    }

    /** Agrega a la etapa {@code cantidad} manzanas con código automático (sigue la numeración). */
    @Transactional
    public List<ManzanaResponse> generarManzanas(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            GenerarRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        EtapaProyecto etapa = etapa(contexto, proyectoId, etapaId);
        if (etapa.getEstado() != EstadoEtapa.ACTIVA) {
            throw new ReglaNegocioException("Solo se pueden agregar manzanas a una etapa activa");
        }
        if (request.cantidad() > 100) {
            throw new ReglaNegocioException("Máximo 100 manzanas en una sola vez");
        }
        List<Manzana> manzanas = crearManzanasAutomaticas(contexto, etapa, request.cantidad());
        avisos.registrar(contexto, proyecto, "MANZANAS_GENERADAS", "ETAPA", etapa.getId(),
                manzanas.size() + " manzanas nuevas en " + etapa.getNombre(),
                "agregó " + manzanas.size() + " manzanas " + rango(manzanas) + " a la etapa " + etapa.getNombre(),
                null, Map.of("manzanas", manzanas.stream().map(Manzana::getCodigo).toList()));
        return manzanas.stream().map(ManzanaResponse::from).toList();
    }

    /**
     * Crea {@code cantidad} lotes en la manzana, numerados después del último (1, 2, 3…) y con
     * código ETAPA-MANZANA-Lnn. Nacen PENDIENTES: sin medidas ni precio, no se pueden reservar
     * hasta completarlos.
     */
    @Transactional
    public List<LoteResponse> generarLotes(
            ContextoUsuario contexto,
            Long proyectoId,
            Long etapaId,
            Long manzanaId,
            GenerarRequest request
    ) {
        Proyecto proyecto = proyectoService.obtenerAutorizado(contexto, proyectoId);
        verificarProyectoOperable(proyecto);
        EtapaProyecto etapa = etapa(contexto, proyectoId, etapaId);
        Manzana manzana = manzana(contexto, proyectoId, etapaId, manzanaId);
        if (etapa.getEstado() != EstadoEtapa.ACTIVA || manzana.getEstado() != EstadoEtapa.ACTIVA) {
            throw new ReglaNegocioException("La etapa y la manzana deben estar activas para agregar lotes");
        }
        int cantidad = request.cantidad();
        long existentes = loteRepository.countByProyectoIdAndEmpresaId(proyectoId, contexto.empresaId());
        if (proyecto.getMaxLotes() != null && existentes + cantidad > proyecto.getMaxLotes()) {
            long libres = Math.max(0, proyecto.getMaxLotes() - existentes);
            throw new ReglaNegocioException("El proyecto admite máximo " + proyecto.getMaxLotes()
                    + " lotes y ya tiene " + existentes + ": puedes crear " + libres + " más");
        }
        String prefijo = etapa.getCodigo() + "-" + manzana.getCodigo() + "-L";
        Set<String> numerosUsados = new HashSet<>();
        int ultimo = 0;
        for (String numero : loteRepository.numerosDeManzana(manzanaId)) {
            numerosUsados.add(numero.trim().toUpperCase(Locale.ROOT));
            ultimo = Math.max(ultimo, entero(numero));
        }
        int ancho = Math.max(2, String.valueOf(ultimo + cantidad).length());
        if (prefijo.length() + ancho > 50) {
            throw new ReglaNegocioException("Los códigos de la etapa y la manzana son muy largos para"
                    + " armar el código de los lotes; acórtalos a menos de 20 caracteres");
        }
        Set<String> codigosUsados = new HashSet<>(loteRepository.codigosConPrefijo(
                proyectoId, prefijo.toUpperCase(Locale.ROOT) + "%"));
        List<Lote> nuevos = new ArrayList<>();
        int n = ultimo;
        while (nuevos.size() < cantidad) {
            n++;
            String numero = String.valueOf(n);
            String codigo = (prefijo + String.format("%0" + ancho + "d", n)).toUpperCase(Locale.ROOT);
            if (numerosUsados.contains(numero) || codigosUsados.contains(codigo)) {
                continue;
            }
            nuevos.add(Lote.pendiente(contexto.empresaId(), proyectoId, etapaId, manzanaId, codigo, numero));
        }
        List<Lote> guardados = loteRepository.saveAll(nuevos);
        estadoRepository.saveAll(guardados.stream()
                .map(lote -> new LoteHistorialEstado(lote.getId(), null, EstadoLote.PENDIENTE,
                        "GENERACION_LOTE", lote.getId(), "Generado en bloque: faltan medidas y precio",
                        contexto.usuarioId()))
                .toList());
        avisos.registrar(contexto, proyecto, "LOTES_GENERADOS", "MANZANA", manzana.getId(),
                guardados.size() + " lotes nuevos en " + manzana.getNombre(),
                "creó " + guardados.size() + " lotes (" + guardados.get(0).getCodigo() + " a "
                        + guardados.get(guardados.size() - 1).getCodigo() + ") en " + manzana.getNombre()
                        + " de " + etapa.getNombre() + "; quedan por completar medidas y precio",
                null, Map.of("lotes", guardados.stream().map(Lote::getCodigo).toList()));
        return guardados.stream().map(LoteResponse::from).toList();
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
        avisos.registrar(contexto, proyecto, "LOTE_CREADO", "LOTE", lote.getId(),
                "Lote " + lote.getCodigo() + " creado",
                "creó el lote " + lote.getCodigo(), null, datosLote(lote));
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
        Map<String, Object> antes = datosLote(lote);
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
        if (request.valorLista() != null
                && (lote.getValorLista() == null || request.valorLista().compareTo(lote.getValorLista()) != 0)) {
            String motivo = normalizarOpcional(request.motivoPrecio());
            registrarPrecio(contexto, lote, request.valorLista(), motivo != null
                    ? motivo
                    : lote.getValorLista() == null ? "Precio inicial del lote" : "Ajuste desde la edición del lote");
        }
        habilitarSiEstaCompleto(contexto, lote);
        Map<String, Object> despues = datosLote(lote);
        if (!antes.equals(despues)) {
            avisos.registrar(contexto, proyecto, "LOTE_EDITADO", "LOTE", lote.getId(),
                    "Lote " + lote.getCodigo() + " actualizado",
                    "actualizó el lote " + lote.getCodigo() + " (" + cambios(antes, despues) + ")",
                    antes, despues);
        }
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
            avisos.registrar(contexto, proyecto, "LOTE_ESTADO", "LOTE", lote.getId(),
                    "Lote " + lote.getCodigo() + ": " + etiqueta(anterior) + " → " + etiqueta(request.estado()),
                    "cambió el lote " + lote.getCodigo() + " de " + etiqueta(anterior) + " a "
                            + etiqueta(request.estado()) + " (" + requerido(request.motivo()) + ")",
                    Map.of("estado", anterior.name()), Map.of("estado", request.estado().name()));
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
        BigDecimal anterior = lote.getValorLista();
        registrarPrecio(contexto, lote, request.valor(), requerido(request.motivo()));
        habilitarSiEstaCompleto(contexto, lote);
        avisos.registrar(contexto, proyecto, "LOTE_PRECIO", "LOTE", lote.getId(),
                "Precio del lote " + lote.getCodigo() + " actualizado",
                "cambió el precio del lote " + lote.getCodigo() + " de " + pesos(anterior) + " a "
                        + pesos(request.valor()) + " (" + requerido(request.motivo()) + ")",
                datos("valorLista", anterior), datos("valorLista", request.valor()));
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
        long pendientes = cantidades.getOrDefault(EstadoLote.PENDIENTE, 0L);
        BigDecimal valor = loteRepository.sumarValorDisponible(
                contexto.empresaId(),
                proyectoId
        );
        return new InventarioResumenResponse(
                disponibles + reservados + vendidos + bloqueados + pendientes,
                disponibles,
                reservados,
                vendidos,
                bloqueados,
                pendientes,
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

    /** Crea las manzanas siguiendo la numeración de la etapa: M01, M02… "Manzana 1", "Manzana 2"… */
    private List<Manzana> crearManzanasAutomaticas(
            ContextoUsuario contexto,
            EtapaProyecto etapa,
            int cantidad
    ) {
        Set<String> usados = new HashSet<>(manzanaRepository.codigosDeEtapa(etapa.getId()));
        int secuencia = manzanaRepository.maxSecuencia(etapa.getId());
        List<Manzana> nuevas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            secuencia++;
            String codigo = siguienteCodigo("M", secuencia, usados);
            usados.add(codigo);
            nuevas.add(new Manzana(contexto.empresaId(), etapa.getProyectoId(), etapa.getId(), codigo,
                    "Manzana " + entero(codigo), secuencia));
        }
        return manzanaRepository.saveAll(nuevas);
    }

    private String codigoEtapaLibre(Long proyectoId, int desde) {
        int n = desde;
        String codigo = "E" + String.format("%02d", n);
        while (etapaRepository.existsByProyectoIdAndCodigoIgnoreCase(proyectoId, codigo)) {
            n++;
            codigo = "E" + String.format("%02d", n);
        }
        return codigo;
    }

    /** Primer código libre del tipo PREFIJO + número de dos cifras, empezando en {@code desde}. */
    private static String siguienteCodigo(String prefijo, int desde, Set<String> usados) {
        int n = Math.max(desde, 1);
        String codigo = prefijo + String.format("%02d", n);
        while (usados.contains(codigo)) {
            n++;
            codigo = prefijo + String.format("%02d", n);
        }
        return codigo;
    }

    /** Cambia el precio (o pone el primero) y lo deja en el historial de precios. */
    private void registrarPrecio(ContextoUsuario contexto, Lote lote, BigDecimal valor, String motivo) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        BigDecimal anterior = lote.cambiarPrecio(valor);
        precioRepository
                .findFirstByLoteIdAndVigenteHastaIsNullOrderByVigenteDesdeDesc(lote.getId())
                .ifPresent(historial -> historial.cerrar(ahora));
        precioRepository.save(new LotePrecioHistorial(
                lote.getId(),
                anterior,
                valor,
                ahora,
                motivo,
                contexto.usuarioId()
        ));
    }

    /** Un lote PENDIENTE con área y precio pasa a DISPONIBLE y queda en su historial. */
    private void habilitarSiEstaCompleto(ContextoUsuario contexto, Lote lote) {
        EstadoLote anterior = lote.habilitarSiEstaCompleto();
        if (anterior != null) {
            estadoRepository.save(new LoteHistorialEstado(
                    lote.getId(),
                    anterior,
                    EstadoLote.DISPONIBLE,
                    "DATOS_COMPLETOS",
                    lote.getId(),
                    "Medidas y precio registrados: el lote queda disponible",
                    contexto.usuarioId()
            ));
        }
    }

    private static Map<String, Object> datosEtapa(EtapaProyecto etapa) {
        return datos("codigo", etapa.getCodigo(), "nombre", etapa.getNombre(), "secuencia", etapa.getSecuencia(),
                "fechaInicio", etapa.getFechaInicio(), "fechaFin", etapa.getFechaFin(),
                "estado", etapa.getEstado() == null ? null : etapa.getEstado().name());
    }

    private static Map<String, Object> datosManzana(Manzana manzana) {
        return datos("codigo", manzana.getCodigo(), "nombre", manzana.getNombre(),
                "secuencia", manzana.getSecuencia(),
                "estado", manzana.getEstado() == null ? null : manzana.getEstado().name());
    }

    private static Map<String, Object> datosLote(Lote lote) {
        return datos("codigo", lote.getCodigo(), "numero", lote.getNumero(),
                "areaM2", lote.getAreaM2(), "frenteM", lote.getFrenteM(), "fondoM", lote.getFondoM(),
                "valorLista", lote.getValorLista(), "estado", lote.getEstado().name(),
                "ubicacionReferencia", lote.getUbicacionReferencia(), "descripcion", lote.getDescripcion());
    }

    /** Mapa ordenado que admite valores nulos; los BigDecimal se normalizan para comparar. */
    private static Map<String, Object> datos(Object... claveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) {
            Object valor = claveValor[i + 1];
            if (valor instanceof BigDecimal decimal) {
                valor = decimal.stripTrailingZeros().toPlainString();
            }
            mapa.put((String) claveValor[i], valor);
        }
        return mapa;
    }

    /** "nombre: Etapa 1 → Etapa Norte, valorLista: — → 45000000". */
    private static String cambios(Map<String, Object> antes, Map<String, Object> despues) {
        List<String> partes = new ArrayList<>();
        for (Map.Entry<String, Object> entrada : despues.entrySet()) {
            Object previo = antes.get(entrada.getKey());
            if (!java.util.Objects.equals(previo, entrada.getValue())) {
                partes.add(entrada.getKey() + ": " + (previo == null ? "—" : previo) + " → "
                        + (entrada.getValue() == null ? "—" : entrada.getValue()));
            }
        }
        return String.join(", ", partes);
    }

    private static String rango(List<Manzana> manzanas) {
        if (manzanas.isEmpty()) {
            return "";
        }
        return manzanas.size() == 1
                ? "(" + manzanas.get(0).getCodigo() + ")"
                : "(" + manzanas.get(0).getCodigo() + " a " + manzanas.get(manzanas.size() - 1).getCodigo() + ")";
    }

    private static String etiqueta(EstadoLote estado) {
        return switch (estado) {
            case PENDIENTE -> "por completar";
            case DISPONIBLE -> "disponible";
            case RESERVADO -> "reservado";
            case VENDIDO -> "vendido";
            case BLOQUEADO -> "bloqueado";
        };
    }

    private static String pesos(BigDecimal valor) {
        if (valor == null) {
            return "sin precio";
        }
        return "$" + String.format(Locale.forLanguageTag("es-CO"), "%,.0f", valor);
    }

    /** Número entero al final del texto ("M07" → 7, "12" → 12); 0 si no hay. */
    private static int entero(String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("^.*?(\\d+)$", "$1");
        if (digitos.isEmpty() || !digitos.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        try {
            return Integer.parseInt(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
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
