package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.FinanzasDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.CategoriaCosto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.CostoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoCosto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoPresupuesto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PresupuestoProyecto;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.CategoriaCostoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.CostoProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.PresupuestoProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanzasGerenciaService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CategoriaCostoRepository categoriaRepository;
    private final PresupuestoProyectoRepository presupuestoRepository;
    private final CostoProyectoRepository costoRepository;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final JdbcTemplate jdbcTemplate;

    public FinanzasGerenciaService(CategoriaCostoRepository categoriaRepository,
                                   PresupuestoProyectoRepository presupuestoRepository,
                                   CostoProyectoRepository costoRepository,
                                   GerenciaAlcanceService alcanceService,
                                   AuditoriaRegistroService auditoria,
                                   JdbcTemplate jdbcTemplate) {
        this.categoriaRepository = categoriaRepository;
        this.presupuestoRepository = presupuestoRepository;
        this.costoRepository = costoRepository;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<FinanzasDtos.CategoriaResponse> categorias(ContextoUsuario contexto) {
        return categoriaRepository.findByEmpresaIdOrderByNombreAsc(contexto.empresaId()).stream()
                .map(FinanzasDtos.CategoriaResponse::from).toList();
    }

    @Transactional
    public FinanzasDtos.CategoriaResponse crearCategoria(ContextoUsuario contexto,
                                                          FinanzasDtos.CategoriaRequest request) {
        alcanceService.verificarEmpresa(contexto);
        String codigo = request.codigo().trim().toUpperCase();
        if (categoriaRepository.existsByEmpresaIdAndCodigo(contexto.empresaId(), codigo)) {
            throw new ConflictoException("Ya existe una categoría con ese código");
        }
        CategoriaCosto categoria = categoriaRepository.save(new CategoriaCosto(contexto.empresaId(),
                codigo, request.nombre(), request.tipo()));
        auditoria.registrar(contexto, null, "GERENCIA", "CATEGORIA_COSTO_CREAR", "CATEGORIA_COSTO",
                categoria.getId(), "Categoría de costo creada", null,
                FinanzasDtos.CategoriaResponse.from(categoria));
        return FinanzasDtos.CategoriaResponse.from(categoria);
    }

    @Transactional
    public FinanzasDtos.CategoriaResponse inactivarCategoria(ContextoUsuario contexto, Long id) {
        alcanceService.verificarEmpresa(contexto);
        CategoriaCosto categoria = categoria(contexto, id);
        categoria.inactivar();
        auditoria.registrar(contexto, null, "GERENCIA", "CATEGORIA_COSTO_INACTIVAR", "CATEGORIA_COSTO",
                categoria.getId(), "Categoría de costo inactivada", true, false);
        return FinanzasDtos.CategoriaResponse.from(categoria);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FinanzasDtos.PresupuestoResponse> presupuestos(
            ContextoUsuario contexto, Long proyectoId, EstadoPresupuesto estado,
            int pagina, int tamano) {
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        Specification<PresupuestoProyecto> filtro = (root, query, cb) ->
                cb.equal(root.get("empresaId"), contexto.empresaId());
        if (proyecto != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("proyectoId"), proyecto));
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        return PaginaResponse.from(presupuestoRepository.findAll(filtro, pagina(pagina, tamano, "periodoDesde")),
                FinanzasDtos.PresupuestoResponse::from);
    }

    @Transactional
    public FinanzasDtos.PresupuestoResponse crearPresupuesto(ContextoUsuario contexto,
                                                               FinanzasDtos.PresupuestoRequest request) {
        alcanceService.verificarProyecto(contexto, request.proyectoId());
        validarCategoria(contexto, request.categoriaCostoId());
        try {
            PresupuestoProyecto presupuesto = presupuestoRepository.saveAndFlush(new PresupuestoProyecto(
                    contexto.empresaId(), request.proyectoId(), request.categoriaCostoId(),
                    request.periodoDesde(), request.periodoHasta(), request.valorPresupuestado(),
                    contexto.usuarioId()));
            auditarPresupuesto(contexto, presupuesto, "PRESUPUESTO_CREAR", "Presupuesto creado");
            return FinanzasDtos.PresupuestoResponse.from(presupuesto);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("Ya existe un presupuesto para la categoría y el periodo");
        }
    }

    @Transactional
    public FinanzasDtos.PresupuestoResponse actualizarPresupuesto(ContextoUsuario contexto, Long id,
                                                                   FinanzasDtos.PresupuestoRequest request) {
        PresupuestoProyecto p = presupuesto(contexto, id);
        if (!p.getProyectoId().equals(request.proyectoId())
                || !p.getCategoriaCostoId().equals(request.categoriaCostoId())) {
            throw new ReglaNegocioException("El proyecto y la categoría no pueden cambiarse");
        }
        p.actualizar(request.periodoDesde(), request.periodoHasta(), request.valorPresupuestado());
        auditarPresupuesto(contexto, p, "PRESUPUESTO_EDITAR", "Presupuesto actualizado");
        return FinanzasDtos.PresupuestoResponse.from(p);
    }

    @Transactional
    public FinanzasDtos.PresupuestoResponse aprobarPresupuesto(ContextoUsuario contexto, Long id) {
        PresupuestoProyecto p = presupuesto(contexto, id);
        p.aprobar(contexto.usuarioId());
        auditarPresupuesto(contexto, p, "PRESUPUESTO_APROBAR", "Presupuesto aprobado");
        return FinanzasDtos.PresupuestoResponse.from(p);
    }

    @Transactional
    public FinanzasDtos.PresupuestoResponse cerrarPresupuesto(ContextoUsuario contexto, Long id) {
        PresupuestoProyecto p = presupuesto(contexto, id);
        if (!p.getPeriodoHasta().isBefore(LocalDate.now(BOGOTA))) {
            throw new ReglaNegocioException("Solo puede cerrarse un presupuesto cuyo periodo finalizó");
        }
        p.cerrar();
        auditarPresupuesto(contexto, p, "PRESUPUESTO_CERRAR", "Presupuesto cerrado");
        return FinanzasDtos.PresupuestoResponse.from(p);
    }

    @Transactional
    public FinanzasDtos.PresupuestoResponse anularPresupuesto(ContextoUsuario contexto, Long id) {
        PresupuestoProyecto p = presupuesto(contexto, id);
        p.anular();
        auditarPresupuesto(contexto, p, "PRESUPUESTO_ANULAR", "Presupuesto anulado");
        return FinanzasDtos.PresupuestoResponse.from(p);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FinanzasDtos.CostoResponse> costos(ContextoUsuario contexto, Long proyectoId,
                                                              EstadoCosto estado, LocalDate desde,
                                                              LocalDate hasta, int pagina, int tamano) {
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        validarFechas(desde, hasta);
        Specification<CostoProyecto> filtro = (root, query, cb) -> cb.equal(root.get("empresaId"), contexto.empresaId());
        if (proyecto != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("proyectoId"), proyecto));
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (desde != null) filtro = filtro.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaCosto"), desde));
        if (hasta != null) filtro = filtro.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaCosto"), hasta));
        return PaginaResponse.from(costoRepository.findAll(filtro, pagina(pagina, tamano, "fechaCosto")),
                FinanzasDtos.CostoResponse::from);
    }

    @Transactional
    public FinanzasDtos.CostoResponse registrarCosto(ContextoUsuario contexto,
                                                      FinanzasDtos.CostoRequest request) {
        alcanceService.verificarProyecto(contexto, request.proyectoId());
        validarCategoria(contexto, request.categoriaCostoId());
        alcanceService.verificarArchivo(contexto, request.comprobanteId());
        CostoProyecto costo = costoRepository.save(new CostoProyecto(contexto.empresaId(),
                request.proyectoId(), request.categoriaCostoId(), request.fechaCosto(), request.concepto(),
                request.proveedor(), request.referencia(), request.valor(), request.comprobanteId(),
                contexto.usuarioId()));
        auditarCosto(contexto, costo, "COSTO_REGISTRAR", "Costo registrado");
        return FinanzasDtos.CostoResponse.from(costo);
    }

    @Transactional
    public FinanzasDtos.CostoResponse aprobarCosto(ContextoUsuario contexto, Long id) {
        CostoProyecto costo = costo(contexto, id);
        costo.aprobar(contexto.usuarioId());
        auditarCosto(contexto, costo, "COSTO_APROBAR", "Costo aprobado");
        return FinanzasDtos.CostoResponse.from(costo);
    }

    @Transactional
    public FinanzasDtos.CostoResponse pagarCosto(ContextoUsuario contexto, Long id) {
        CostoProyecto costo = costo(contexto, id);
        costo.pagar();
        auditarCosto(contexto, costo, "COSTO_PAGAR", "Costo marcado como pagado");
        return FinanzasDtos.CostoResponse.from(costo);
    }

    @Transactional
    public FinanzasDtos.CostoResponse anularCosto(ContextoUsuario contexto, Long id) {
        CostoProyecto costo = costo(contexto, id);
        costo.anular();
        auditarCosto(contexto, costo, "COSTO_ANULAR", "Costo anulado");
        return FinanzasDtos.CostoResponse.from(costo);
    }

    @Transactional(readOnly = true)
    public FinanzasDtos.RentabilidadResponse rentabilidad(ContextoUsuario contexto, Long proyectoId,
                                                           LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new ReglaNegocioException("El periodo es obligatorio");
        validarFechas(desde, hasta);
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        if (proyecto == null) alcanceService.verificarEmpresa(contexto);
        BigDecimal ventas = valor("""
                SELECT COALESCE(SUM(valor_total),0) FROM contratos
                WHERE empresa_id = ? AND estado IN ('VIGENTE','FINALIZADO')
                  AND fecha_firma BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), desde, hasta, proyecto, proyecto);
        BigDecimal recaudo = valor("""
                SELECT COALESCE(SUM(p.valor),0) FROM pagos p
                LEFT JOIN contratos c ON c.id = p.contrato_id
                WHERE p.empresa_id = ? AND p.estado = 'CONFIRMADO'
                  AND DATE(p.fecha_pago) BETWEEN ? AND ?
                  AND (? IS NULL OR c.proyecto_id = ?)
                """, contexto.empresaId(), desde, hasta, proyecto, proyecto);
        BigDecimal presupuesto = valor("""
                SELECT COALESCE(SUM(valor_presupuestado),0) FROM presupuestos_proyecto
                WHERE empresa_id = ? AND estado IN ('APROBADO','CERRADO')
                  AND periodo_desde <= ? AND periodo_hasta >= ?
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), hasta, desde, proyecto, proyecto);
        BigDecimal costos = valor("""
                SELECT COALESCE(SUM(valor),0) FROM costos_proyecto
                WHERE empresa_id = ? AND estado IN ('APROBADO','PAGADO')
                  AND fecha_costo BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), desde, hasta, proyecto, proyecto);
        BigDecimal utilidad = ventas.subtract(costos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal desviacion = presupuesto.subtract(costos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margen = ventas.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : utilidad.multiply(BigDecimal.valueOf(100)).divide(ventas, 2, RoundingMode.HALF_UP);
        return new FinanzasDtos.RentabilidadResponse(proyecto, desde, hasta, ventas, recaudo,
                presupuesto, costos, desviacion, utilidad, margen);
    }

    private CategoriaCosto categoria(ContextoUsuario contexto, Long id) {
        return categoriaRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría de costo no encontrada"));
    }

    private void validarCategoria(ContextoUsuario contexto, Long id) {
        CategoriaCosto categoria = categoria(contexto, id);
        if (!categoria.isActivo()) throw new ReglaNegocioException("La categoría de costo está inactiva");
    }

    private PresupuestoProyecto presupuesto(ContextoUsuario contexto, Long id) {
        PresupuestoProyecto p = presupuestoRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto no encontrado"));
        alcanceService.verificarProyecto(contexto, p.getProyectoId());
        return p;
    }

    private CostoProyecto costo(ContextoUsuario contexto, Long id) {
        CostoProyecto c = costoRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Costo no encontrado"));
        alcanceService.verificarProyecto(contexto, c.getProyectoId());
        return c;
    }

    private BigDecimal valor(String sql, Object... parametros) {
        BigDecimal valor = jdbcTemplate.queryForObject(sql, BigDecimal.class, parametros);
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial");
        }
    }

    private void auditarPresupuesto(ContextoUsuario c, PresupuestoProyecto p, String accion, String descripcion) {
        auditoria.registrar(c, p.getProyectoId(), "GERENCIA", accion, "PRESUPUESTO", p.getId(),
                descripcion, null, FinanzasDtos.PresupuestoResponse.from(p));
    }

    private void auditarCosto(ContextoUsuario c, CostoProyecto costo, String accion, String descripcion) {
        auditoria.registrar(c, costo.getProyectoId(), "GERENCIA", accion, "COSTO_PROYECTO", costo.getId(),
                descripcion, null, FinanzasDtos.CostoResponse.from(costo));
    }

    private static PageRequest pagina(int pagina, int tamano, String orden) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, orden));
    }
}
