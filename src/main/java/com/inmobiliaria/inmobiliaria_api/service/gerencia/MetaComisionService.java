package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ComisionMetaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Comision;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoComision;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoMeta;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.MetaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReglaComision;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ComisionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.MetaComercialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ReglaComisionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetaComisionService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ReglaComisionRepository reglaRepository;
    private final ComisionRepository comisionRepository;
    private final MetaComercialRepository metaRepository;
    private final GerenciaAlcanceService alcanceService;
    private final AuditoriaRegistroService auditoria;
    private final JdbcTemplate jdbcTemplate;

    public MetaComisionService(ReglaComisionRepository reglaRepository,
                               ComisionRepository comisionRepository,
                               MetaComercialRepository metaRepository,
                               GerenciaAlcanceService alcanceService,
                               AuditoriaRegistroService auditoria,
                               JdbcTemplate jdbcTemplate) {
        this.reglaRepository = reglaRepository;
        this.comisionRepository = comisionRepository;
        this.metaRepository = metaRepository;
        this.alcanceService = alcanceService;
        this.auditoria = auditoria;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComisionMetaDtos.ReglaResponse> listarReglas(
            ContextoUsuario contexto, Long proyectoId, Boolean activo, int pagina, int tamano) {
        if (proyectoId != null) alcanceService.verificarProyecto(contexto, proyectoId);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        Specification<ReglaComision> filtro = (root, query, cb) ->
                cb.equal(root.get("empresaId"), contexto.empresaId());
        if (proyectoId != null) {
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.isNull(root.get("proyectoId")), cb.equal(root.get("proyectoId"), proyectoId)));
        } else if (!alcance.empresaCompleta()) {
            filtro = filtro.and((root, query, cb) -> alcance.proyectos().isEmpty()
                    ? cb.isNull(root.get("proyectoId"))
                    : cb.or(cb.isNull(root.get("proyectoId")), root.get("proyectoId").in(alcance.proyectos())));
        }
        if (activo != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        Page<ReglaComision> resultado = reglaRepository.findAll(filtro, pagina(pagina, tamano, "vigenteDesde"));
        return PaginaResponse.from(resultado, ComisionMetaDtos.ReglaResponse::from);
    }

    @Transactional
    public ComisionMetaDtos.ReglaResponse crearRegla(ContextoUsuario contexto,
                                                      ComisionMetaDtos.ReglaRequest request) {
        if (request.proyectoId() == null) alcanceService.verificarEmpresa(contexto);
        else alcanceService.verificarProyecto(contexto, request.proyectoId());
        ReglaComision regla = reglaRepository.save(new ReglaComision(contexto.empresaId(),
                request.proyectoId(), request.nombre(), request.tipo(), request.valor(),
                request.condicionCausacion().trim().toUpperCase(), request.vigenteDesde(),
                request.vigenteHasta()));
        auditoria.registrar(contexto, regla.getProyectoId(), "GERENCIA", "REGLA_COMISION_CREAR",
                "REGLA_COMISION", regla.getId(), "Regla de comisión creada", null,
                ComisionMetaDtos.ReglaResponse.from(regla));
        return ComisionMetaDtos.ReglaResponse.from(regla);
    }

    @Transactional
    public ComisionMetaDtos.ReglaResponse actualizarRegla(ContextoUsuario contexto, Long id,
                                                           ComisionMetaDtos.ReglaRequest request) {
        ReglaComision regla = regla(contexto, id);
        alcanceService.verificarEmpresa(contexto);
        if (!java.util.Objects.equals(regla.getProyectoId(), request.proyectoId())) {
            throw new ReglaNegocioException("El proyecto de una regla existente no puede cambiarse");
        }
        var anterior = ComisionMetaDtos.ReglaResponse.from(regla);
        regla.actualizar(request.nombre(), request.tipo(), request.valor(),
                request.condicionCausacion().trim().toUpperCase(), request.vigenteDesde(),
                request.vigenteHasta());
        auditoria.registrar(contexto, regla.getProyectoId(), "GERENCIA", "REGLA_COMISION_EDITAR",
                "REGLA_COMISION", regla.getId(), "Regla de comisión actualizada", anterior,
                ComisionMetaDtos.ReglaResponse.from(regla));
        return ComisionMetaDtos.ReglaResponse.from(regla);
    }

    @Transactional
    public ComisionMetaDtos.ReglaResponse cerrarRegla(ContextoUsuario contexto, Long id,
                                                       LocalDate fecha) {
        ReglaComision regla = regla(contexto, id);
        alcanceService.verificarEmpresa(contexto);
        regla.cerrar(fecha);
        auditoria.registrar(contexto, regla.getProyectoId(), "GERENCIA", "REGLA_COMISION_CERRAR",
                "REGLA_COMISION", regla.getId(), "Vigencia de regla cerrada", null,
                ComisionMetaDtos.ReglaResponse.from(regla));
        return ComisionMetaDtos.ReglaResponse.from(regla);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComisionMetaDtos.ComisionResponse> listarComisiones(
            ContextoUsuario contexto, Long proyectoId, Long asesorId, EstadoComision estado,
            int pagina, int tamano) {
        if (proyectoId != null) alcanceService.verificarProyecto(contexto, proyectoId);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        Specification<Comision> filtro = (root, query, cb) ->
                cb.equal(root.get("empresaId"), contexto.empresaId());
        if (proyectoId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("proyectoId"), proyectoId));
        if (asesorId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("asesorId"), asesorId));
        if (!alcance.empresaCompleta() && proyectoId == null) {
            filtro = filtro.and((root, query, cb) -> {
                var permitidos = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(root.get("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) permitidos.add(cb.equal(root.get("asesorId"), contexto.usuarioId()));
                return permitidos.isEmpty() ? cb.disjunction()
                        : cb.or(permitidos.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        Page<Comision> resultado = comisionRepository.findAll(filtro, pagina(pagina, tamano, "creadoEn"));
        return PaginaResponse.from(resultado, ComisionMetaDtos.ComisionResponse::from);
    }

    @Transactional
    public ComisionMetaDtos.ComisionResponse proyectar(ContextoUsuario contexto,
                                                        ComisionMetaDtos.ProyectarComisionRequest request) {
        ContratoFila contrato = contratoParaActualizar(contexto, request.contratoId());
        if (contrato.asesorId() == null) {
            throw new ReglaNegocioException("El contrato no tiene asesor asociado");
        }
        ReglaComision regla = regla(contexto, request.reglaComisionId());
        if (regla.getProyectoId() != null && !regla.getProyectoId().equals(contrato.proyectoId())) {
            throw new ReglaNegocioException("La regla no corresponde al proyecto del contrato");
        }
        if (!regla.vigente(LocalDate.now(BOGOTA))) {
            throw new ReglaNegocioException("La regla no está vigente");
        }
        BigDecimal base = request.baseCalculo() == null ? contrato.valorTotal() : request.baseCalculo();
        if (base.compareTo(contrato.valorTotal()) > 0) {
            throw new ReglaNegocioException("La base de comisión no puede superar el valor del contrato");
        }
        if (comisionRepository.existsByContratoIdAndAsesorIdAndReglaComisionId(
                contrato.id(), contrato.asesorId(), regla.getId())) {
            throw new ConflictoException("La comisión ya fue proyectada para este contrato, asesor y regla");
        }
        Comision comision;
        try {
            comision = comisionRepository.saveAndFlush(new Comision(contexto.empresaId(),
                    contrato.proyectoId(), contrato.asesorId(), contrato.id(), regla, base));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("La comisión ya existe");
        }
        auditoria.registrar(contexto, contrato.proyectoId(), "GERENCIA", "COMISION_PROYECTAR",
                "COMISION", comision.getId(), "Comisión proyectada", null,
                ComisionMetaDtos.ComisionResponse.from(comision));
        return ComisionMetaDtos.ComisionResponse.from(comision);
    }

    @Transactional
    public ComisionMetaDtos.ComisionResponse causar(ContextoUsuario contexto, Long id, LocalDate fecha) {
        Comision comision = comision(contexto, id);
        ReglaComision regla = regla(contexto, comision.getReglaComisionId());
        validarCausacion(comision, regla);
        comision.causar(fecha);
        auditarComision(contexto, comision, "COMISION_CAUSAR", "Comisión causada");
        return ComisionMetaDtos.ComisionResponse.from(comision);
    }

    @Transactional
    public ComisionMetaDtos.ComisionResponse aprobar(ContextoUsuario contexto, Long id) {
        Comision comision = comision(contexto, id);
        comision.aprobar(contexto.usuarioId());
        auditarComision(contexto, comision, "COMISION_APROBAR", "Comisión aprobada");
        return ComisionMetaDtos.ComisionResponse.from(comision);
    }

    @Transactional
    public ComisionMetaDtos.ComisionResponse pagar(ContextoUsuario contexto, Long id, LocalDate fecha) {
        Comision comision = comision(contexto, id);
        alcanceService.verificarEmpresa(contexto);
        comision.pagar(fecha);
        auditarComision(contexto, comision, "COMISION_PAGAR", "Comisión marcada como pagada");
        return ComisionMetaDtos.ComisionResponse.from(comision);
    }

    @Transactional
    public ComisionMetaDtos.ComisionResponse anular(ContextoUsuario contexto, Long id) {
        Comision comision = comision(contexto, id);
        comision.anular();
        auditarComision(contexto, comision, "COMISION_ANULAR", "Comisión anulada");
        return ComisionMetaDtos.ComisionResponse.from(comision);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComisionMetaDtos.MetaResponse> listarMetas(
            ContextoUsuario contexto, String periodo, Long proyectoId, Long asesorId,
            EstadoMeta estado, int pagina, int tamano) {
        if (proyectoId != null) alcanceService.verificarProyecto(contexto, proyectoId);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        Specification<MetaComercial> filtro = (root, query, cb) ->
                cb.equal(root.get("empresaId"), contexto.empresaId());
        if (periodo != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("periodo"), periodo));
        if (proyectoId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("proyectoId"), proyectoId));
        if (asesorId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("asesorId"), asesorId));
        if (!alcance.empresaCompleta() && proyectoId == null) {
            filtro = filtro.and((root, query, cb) -> {
                var permitidos = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(root.get("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) permitidos.add(cb.equal(root.get("asesorId"), contexto.usuarioId()));
                return permitidos.isEmpty() ? cb.disjunction()
                        : cb.or(permitidos.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        return PaginaResponse.from(metaRepository.findAll(filtro, pagina(pagina, tamano, "periodo")),
                ComisionMetaDtos.MetaResponse::from);
    }

    @Transactional
    public ComisionMetaDtos.MetaResponse crearMeta(ContextoUsuario contexto,
                                                    ComisionMetaDtos.MetaRequest request) {
        if (request.proyectoId() == null) alcanceService.verificarEmpresa(contexto);
        else alcanceService.verificarProyecto(contexto, request.proyectoId());
        if (request.asesorId() != null) alcanceService.verificarUsuarioEmpresa(contexto, request.asesorId());
        try {
            MetaComercial meta = metaRepository.saveAndFlush(new MetaComercial(contexto.empresaId(),
                    request.proyectoId(), request.asesorId(), request.periodo(), request.valorMeta(),
                    request.cantidadMeta()));
            auditoria.registrar(contexto, meta.getProyectoId(), "GERENCIA", "META_CREAR", "META_COMERCIAL",
                    meta.getId(), "Meta comercial creada", null, ComisionMetaDtos.MetaResponse.from(meta));
            return ComisionMetaDtos.MetaResponse.from(meta);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("Ya existe una meta para el mismo ámbito y periodo");
        }
    }

    @Transactional
    public ComisionMetaDtos.MetaResponse actualizarMeta(ContextoUsuario contexto, Long id,
                                                         ComisionMetaDtos.MetaValoresRequest request) {
        MetaComercial meta = meta(contexto, id);
        meta.actualizar(request.valorMeta(), request.cantidadMeta());
        auditoria.registrar(contexto, meta.getProyectoId(), "GERENCIA", "META_EDITAR", "META_COMERCIAL",
                meta.getId(), "Meta comercial actualizada", null, ComisionMetaDtos.MetaResponse.from(meta));
        return ComisionMetaDtos.MetaResponse.from(meta);
    }

    @Transactional
    public ComisionMetaDtos.MetaResponse aprobarMeta(ContextoUsuario contexto, Long id) {
        MetaComercial meta = meta(contexto, id);
        meta.aprobar(contexto.usuarioId());
        auditoria.registrar(contexto, meta.getProyectoId(), "GERENCIA", "META_APROBAR", "META_COMERCIAL",
                meta.getId(), "Meta comercial aprobada", null, ComisionMetaDtos.MetaResponse.from(meta));
        return ComisionMetaDtos.MetaResponse.from(meta);
    }

    @Transactional
    public ComisionMetaDtos.MetaResponse cerrarMeta(ContextoUsuario contexto, Long id) {
        MetaComercial meta = meta(contexto, id);
        meta.cerrar(YearMonth.now(BOGOTA));
        auditoria.registrar(contexto, meta.getProyectoId(), "GERENCIA", "META_CERRAR", "META_COMERCIAL",
                meta.getId(), "Meta comercial cerrada", null, ComisionMetaDtos.MetaResponse.from(meta));
        return ComisionMetaDtos.MetaResponse.from(meta);
    }

    @Transactional(readOnly = true)
    public ComisionMetaDtos.CumplimientoMetaResponse cumplimiento(ContextoUsuario contexto, Long id) {
        MetaComercial meta = meta(contexto, id);
        YearMonth periodo = YearMonth.parse(meta.getPeriodo());
        LocalDate desde = periodo.atDay(1);
        LocalDate hasta = periodo.atEndOfMonth();
        Logro logro = jdbcTemplate.query("""
                SELECT COALESCE(SUM(valor_total),0) valor, COUNT(*) cantidad
                FROM contratos
                WHERE empresa_id = ?
                  AND estado IN ('VIGENTE','FINALIZADO')
                  AND fecha_firma BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?)
                  AND (? IS NULL OR asesor_id = ?)
                """, rs -> rs.next() ? new Logro(rs.getBigDecimal("valor"), rs.getLong("cantidad"))
                : new Logro(BigDecimal.ZERO, 0), contexto.empresaId(), desde, hasta,
                meta.getProyectoId(), meta.getProyectoId(), meta.getAsesorId(), meta.getAsesorId());
        return new ComisionMetaDtos.CumplimientoMetaResponse(meta.getId(), meta.getPeriodo(),
                meta.getProyectoId(), meta.getAsesorId(), meta.getValorMeta(), logro.valor(),
                porcentaje(logro.valor(), meta.getValorMeta()), meta.getCantidadMeta(), logro.cantidad(),
                porcentaje(BigDecimal.valueOf(logro.cantidad()), BigDecimal.valueOf(meta.getCantidadMeta())),
                meta.getEstado());
    }

    private void validarCausacion(Comision comision, ReglaComision regla) {
        String condicion = regla.getCondicionCausacion();
        if ("CONTRATO_VIGENTE".equals(condicion) || "CONTRATO_FIRMADO".equals(condicion)) {
            Integer total = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM contratos
                    WHERE id = ? AND empresa_id = ? AND estado IN ('VIGENTE','FINALIZADO')
                    """, Integer.class, comision.getContratoId(), comision.getEmpresaId());
            if (total == null || total == 0) throw new ReglaNegocioException("El contrato aún no está vigente");
        } else if ("CUOTA_INICIAL_PAGADA".equals(condicion)) {
            BigDecimal faltante = jdbcTemplate.queryForObject("""
                    SELECT GREATEST(c.cuota_inicial - COALESCE(SUM(pa.valor_aplicado),0), 0)
                    FROM contratos c
                    LEFT JOIN pagos p ON p.contrato_id = c.id AND p.estado = 'CONFIRMADO'
                    LEFT JOIN pago_aplicaciones pa ON pa.pago_id = p.id AND pa.concepto = 'INICIAL'
                    WHERE c.id = ? AND c.empresa_id = ?
                    GROUP BY c.id, c.cuota_inicial
                    """, BigDecimal.class, comision.getContratoId(), comision.getEmpresaId());
            if (faltante == null || faltante.signum() > 0) {
                throw new ReglaNegocioException("La cuota inicial no está pagada completamente");
            }
        } else if ("RECAUDO_TOTAL".equals(condicion)) {
            BigDecimal faltante = jdbcTemplate.queryForObject("""
                    SELECT GREATEST(c.valor_total - COALESCE(SUM(p.valor),0), 0)
                    FROM contratos c
                    LEFT JOIN pagos p ON p.contrato_id = c.id AND p.estado = 'CONFIRMADO'
                    WHERE c.id = ? AND c.empresa_id = ?
                    GROUP BY c.id, c.valor_total
                    """, BigDecimal.class, comision.getContratoId(), comision.getEmpresaId());
            if (faltante == null || faltante.signum() > 0) {
                throw new ReglaNegocioException("El contrato todavía tiene saldo pendiente");
            }
        } else {
            throw new ReglaNegocioException("La condición de causación no está soportada: " + condicion);
        }
    }

    private ReglaComision regla(ContextoUsuario contexto, Long id) {
        ReglaComision regla = reglaRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Regla de comisión no encontrada"));
        if (regla.getProyectoId() != null) alcanceService.verificarProyecto(contexto, regla.getProyectoId());
        return regla;
    }

    private Comision comision(ContextoUsuario contexto, Long id) {
        Comision comision = comisionRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Comisión no encontrada"));
        alcanceService.verificarProyecto(contexto, comision.getProyectoId());
        return comision;
    }

    private MetaComercial meta(ContextoUsuario contexto, Long id) {
        MetaComercial meta = metaRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Meta comercial no encontrada"));
        if (meta.getProyectoId() != null) alcanceService.verificarProyecto(contexto, meta.getProyectoId());
        else {
            AlcanceProyectos alcance = alcanceService.obtener(contexto);
            if (!alcance.empresaCompleta()
                    && !(alcance.registrosPropios() && contexto.usuarioId().equals(meta.getAsesorId()))) {
                alcanceService.verificarEmpresa(contexto);
            }
        }
        return meta;
    }

    private ContratoFila contratoParaActualizar(ContextoUsuario contexto, Long id) {
        ContratoFila contrato = jdbcTemplate.query("""
                SELECT id, proyecto_id, asesor_id, valor_total, estado
                FROM contratos WHERE id = ? AND empresa_id = ? FOR UPDATE
                """, rs -> rs.next() ? new ContratoFila(rs.getLong("id"), rs.getLong("proyecto_id"),
                rs.getObject("asesor_id", Long.class), rs.getBigDecimal("valor_total"),
                rs.getString("estado")) : null, id, contexto.empresaId());
        if (contrato == null) throw new RecursoNoEncontradoException("Contrato no encontrado");
        alcanceService.verificarProyecto(contexto, contrato.proyectoId());
        if (!"VIGENTE".equals(contrato.estado()) && !"FINALIZADO".equals(contrato.estado())) {
            throw new ReglaNegocioException("La comisión solo se proyecta sobre un contrato vigente o finalizado");
        }
        return contrato;
    }

    private void auditarComision(ContextoUsuario contexto, Comision c, String accion, String descripcion) {
        auditoria.registrar(contexto, c.getProyectoId(), "GERENCIA", accion, "COMISION", c.getId(),
                descripcion, null, ComisionMetaDtos.ComisionResponse.from(c));
    }

    private static PageRequest pagina(int pagina, int tamano, String orden) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, orden));
    }

    private static BigDecimal porcentaje(BigDecimal logrado, BigDecimal meta) {
        if (meta == null || meta.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return logrado.multiply(BigDecimal.valueOf(100)).divide(meta, 2, RoundingMode.HALF_UP);
    }

    private record ContratoFila(Long id, Long proyectoId, Long asesorId,
                                BigDecimal valorTotal, String estado) { }
    private record Logro(BigDecimal valor, long cantidad) { }
}
