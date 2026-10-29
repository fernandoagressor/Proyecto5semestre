package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.CierreCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.DocumentoCarteraRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.DocumentoCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.GestionCobroRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.GestionCobroResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MoraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.RefinanciacionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.RefinanciacionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.SeguimientoCuotaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuentaGenerado;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoRefinanciacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.OrigenPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PazSalvo;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Refinanciacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.RefinanciacionCuota;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.CuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.EstadoCuentaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.GestionCobroRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PazSalvoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PlanPagoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.RefinanciacionCuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.RefinanciacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarteraGestionService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final List<EstadoCuota> ESTADOS_MORA = List.of(EstadoCuota.PENDIENTE, EstadoCuota.PARCIAL, EstadoCuota.VENCIDA);

    static final String SECUENCIA_GESTIONES = "gestiones_cobro";
    static final String SECUENCIA_ESTADOS_CUENTA = "estados_cuenta";
    private final CuotaRepository cuotaRepository;
    private final PlanPagoRepository planRepository;
    private final GestionCobroRepository gestionRepository;
    private final RefinanciacionRepository refinanciacionRepository;
    private final RefinanciacionCuotaRepository refinanciacionCuotaRepository;
    private final EstadoCuentaRepository estadoCuentaRepository;
    private final SecuenciaService secuencias;
    private final PazSalvoRepository pazSalvoRepository;
    private final CarteraAlcanceService alcanceService;
    private final AlcanceProyectoService alcanceProyectoService;
    private final PlanPagoService planPagoService;
    private final CuotaCalculadora calculadora;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CarteraGestionService(CuotaRepository cuotaRepository, PlanPagoRepository planRepository, GestionCobroRepository gestionRepository, RefinanciacionRepository refinanciacionRepository, RefinanciacionCuotaRepository refinanciacionCuotaRepository, EstadoCuentaRepository estadoCuentaRepository, PazSalvoRepository pazSalvoRepository, CarteraAlcanceService alcanceService, AlcanceProyectoService alcanceProyectoService, PlanPagoService planPagoService, CuotaCalculadora calculadora, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, SecuenciaService secuencias) {
        this.cuotaRepository = cuotaRepository;
        this.planRepository = planRepository;
        this.gestionRepository = gestionRepository;
        this.refinanciacionRepository = refinanciacionRepository;
        this.refinanciacionCuotaRepository = refinanciacionCuotaRepository;
        this.estadoCuentaRepository = estadoCuentaRepository;
        this.secuencias = secuencias;
        this.pazSalvoRepository = pazSalvoRepository;
        this.alcanceService = alcanceService;
        this.alcanceProyectoService = alcanceProyectoService;
        this.planPagoService = planPagoService;
        this.calculadora = calculadora;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int actualizarMora(ContextoUsuario contexto) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
        return actualizarMoraEmpresa(contexto.empresaId(), LocalDate.now(BOGOTA).minusDays(1));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MoraResponse> listarMora(ContextoUsuario contexto, Long contratoId, int pagina, int tamano) {
        Specification<Cuota> filtro = (root, query, cb) -> cb.and(
                cb.equal(root.get("empresaId"), contexto.empresaId()),
                cb.equal(root.get("estado"), EstadoCuota.VENCIDA),
                cb.greaterThan(root.get("saldo"), BigDecimal.ZERO)
        );
        if (contratoId != null) {
            alcanceService.contrato(contexto, contratoId);
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("contratoId"), contratoId));
        } else {
            List<Long> contratos = contratosAutorizados(contexto);
            filtro = filtro.and((root, query, cb) ->
                    contratos.isEmpty() ? cb.disjunction() : root.get("contratoId").in(contratos));
        }
        Page<Cuota> resultado = cuotaRepository.findAll(
                filtro,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), Sort.by("fechaVencimiento").ascending())
        );
        return PaginaResponse.from(resultado, MoraResponse::from);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SeguimientoCuotaResponse> listarSeguimiento(
            ContextoUsuario contexto,
            Long contratoId,
            int pagina,
            int tamano
    ) {
        Specification<Cuota> filtro = (root, query, cb) -> cb.and(
                cb.equal(root.get("empresaId"), contexto.empresaId()),
                root.get("estado").in(ESTADOS_MORA),
                cb.or(
                        cb.greaterThan(root.get("saldo"), BigDecimal.ZERO),
                        cb.greaterThan(root.get("interesMora"), BigDecimal.ZERO)
                )
        );
        if (contratoId != null) {
            alcanceService.contrato(contexto, contratoId);
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("contratoId"), contratoId));
        } else {
            List<Long> contratos = contratosAutorizados(contexto);
            filtro = filtro.and((root, query, cb) ->
                    contratos.isEmpty() ? cb.disjunction() : root.get("contratoId").in(contratos));
        }
        Page<Cuota> resultado = cuotaRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by("fechaVencimiento").ascending().and(Sort.by("numero").ascending())
                )
        );
        LocalDate hoy = LocalDate.now(BOGOTA);
        return PaginaResponse.from(resultado, cuota -> SeguimientoCuotaResponse.from(cuota, hoy));
    }

    @Transactional
    public int actualizarMoraEmpresa(Long empresaId, LocalDate fecha) {
        int procesadas = 0;
        int pagina = 0;
        while (true) {
            List<Cuota> lote = cuotaRepository.findVencidasForUpdate(empresaId, fecha, ESTADOS_MORA, PageRequest.of(pagina, 200));
            if (lote.isEmpty()) break;
            for (Cuota cuota : lote) {
                PlanPago plan = planRepository.findById(cuota.getPlanPagoId()).orElseThrow(() -> new ReglaNegocioException("La cuota no tiene plan de pago"));
                LocalDate ultimoCorte = cuota.getMoraCalculadaHasta() == null
                        ? cuota.getFechaVencimiento()
                        : cuota.getMoraCalculadaHasta();
                int diasIncrementales = fecha.isAfter(ultimoCorte)
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(ultimoCorte, fecha)
                        : 0;
                BigDecimal moraAcumulada = cuota.getInteresMora().add(
                        calculadora.mora(
                                cuota.getSaldo(),
                                plan.getTasaMoraEfectivaAnual(),
                                diasIncrementales
                        )
                );
                cuota.actualizarMora(fecha, moraAcumulada);
                procesadas++;
            }
            cuotaRepository.flush();
            if (lote.size() < 200) break;
            pagina++;
        }
        return procesadas;
    }

    @Transactional
    public GestionCobroResponse registrarGestion(ContextoUsuario contexto, GestionCobroRequest request) {
        Contrato contrato = alcanceService.contratoParaActualizar(contexto, request.contratoId());
        if (request.cuotaId() != null) {
            Cuota cuota = cuotaRepository.findForUpdate(request.cuotaId(), contexto.empresaId()).orElseThrow(() -> new ReglaNegocioException("La cuota no existe"));
            if (!cuota.getContratoId().equals(request.contratoId())) throw new ReglaNegocioException("La cuota no pertenece al contrato");
            BigDecimal totalPendiente = cuota.getSaldo().add(cuota.getInteresMora());
            if (!ESTADOS_MORA.contains(cuota.getEstado()) || totalPendiente.signum() <= 0) {
                throw new ReglaNegocioException("La cuota no tiene saldo pendiente para gestionar");
            }
            if (request.promesaPagoValor() != null
                    && request.promesaPagoValor().compareTo(totalPendiente) > 0) {
                throw new ReglaNegocioException("La promesa supera el saldo pendiente de la cuota");
            }
        }
        if (request.promesaPagoFecha() != null && request.promesaPagoFecha().isBefore(LocalDate.now(BOGOTA))) {
            throw new ReglaNegocioException("La fecha de la promesa no puede estar en el pasado");
        }
        GestionCobro gestion = new GestionCobro(
                contexto.empresaId(), request.contratoId(), request.cuotaId(), contexto.usuarioId(), request.canal(),
                request.resultado().trim(), normalizar(request.detalle()), request.promesaPagoFecha(), request.promesaPagoValor(), LocalDateTime.now(BOGOTA)
        );
        // MongoDB (rama 9): es lo último que hace el método, así una validación fallida no deja la gestión.
        gestion.identificar(secuencias.siguiente(SECUENCIA_GESTIONES), contrato.getProyectoId());
        return GestionCobroResponse.from(gestionRepository.insertar(gestion));
    }

    @Transactional(readOnly = true)
    public List<GestionCobroResponse> listarGestiones(ContextoUsuario contexto, Long contratoId) {
        alcanceService.contrato(contexto, contratoId);
        return gestionRepository.listarPorContrato(contexto.empresaId(), contratoId).stream().map(GestionCobroResponse::from).toList();
    }

    @Transactional
    public RefinanciacionResponse proponerRefinanciacion(ContextoUsuario contexto, RefinanciacionCrearRequest request) {
        Contrato contrato = alcanceService.contratoParaActualizar(contexto, request.contratoId());
        PlanPago activo = planRepository.findActivoForUpdate(contrato.getId(), contexto.empresaId(), EstadoPlanPago.ACTIVO)
                .orElseThrow(() -> new ReglaNegocioException("El contrato no tiene plan activo"));
        BigDecimal saldo = planPagoService.saldoContrato(contrato.getId());
        if (saldo.signum() <= 0) throw new ReglaNegocioException("El contrato no tiene saldo para refinanciar");
        Refinanciacion refinanciacion = refinanciacionRepository.saveAndFlush(new Refinanciacion(
                contexto.empresaId(), generarCodigo("REF"), contrato.getId(), saldo, request.numeroCuotas(),
                request.tasaEfectivaAnual(), request.fechaPrimeraCuota(), request.motivo().trim(), contexto.usuarioId()
        ));
        List<RefinanciacionCuota> cuotas = calculadora.generar(saldo, request.numeroCuotas(), request.tasaEfectivaAnual(), Periodicidad.MENSUAL, request.fechaPrimeraCuota())
                .stream().map(c -> new RefinanciacionCuota(refinanciacion.getId(), c.numero(), c.fechaVencimiento(), c.capital(), c.interes())).toList();
        refinanciacionCuotaRepository.saveAll(cuotas);
        return RefinanciacionResponse.from(refinanciacion, cuotas);
    }

    @Transactional(readOnly = true)
    public List<RefinanciacionResponse> listarRefinanciaciones(ContextoUsuario contexto) {
        return refinanciacionRepository.findByEmpresaIdOrderByCreadoEnDesc(contexto.empresaId()).stream()
                .filter(r -> { alcanceService.contrato(contexto, r.getContratoId()); return true; })
                .map(this::respuestaRefinanciacion).toList();
    }

    @Transactional
    public RefinanciacionResponse aprobarRefinanciacion(ContextoUsuario contexto, Long refinanciacionId) {
        Refinanciacion r = obtenerRefinanciacion(contexto, refinanciacionId);
        r.aprobar(contexto.usuarioId(), LocalDateTime.now(BOGOTA));
        return respuestaRefinanciacion(r);
    }

    @Transactional
    public RefinanciacionResponse rechazarRefinanciacion(ContextoUsuario contexto, Long refinanciacionId) {
        Refinanciacion r = obtenerRefinanciacion(contexto, refinanciacionId);
        r.rechazar(contexto.usuarioId(), LocalDateTime.now(BOGOTA));
        return respuestaRefinanciacion(r);
    }

    @Transactional
    public RefinanciacionResponse aplicarRefinanciacion(ContextoUsuario contexto, Long refinanciacionId) {
        Refinanciacion r = obtenerRefinanciacion(contexto, refinanciacionId);
        if (r.getEstado() != EstadoRefinanciacion.APROBADA) throw new ReglaNegocioException("La refinanciación debe estar aprobada");
        PlanPago actual = planRepository.findActivoForUpdate(r.getContratoId(), contexto.empresaId(), EstadoPlanPago.ACTIVO)
                .orElseThrow(() -> new ReglaNegocioException("El contrato no tiene plan activo"));
        BigDecimal saldoActual = planPagoService.saldoContrato(r.getContratoId());
        if (saldoActual.compareTo(r.getSaldoRefinanciado()) != 0) throw new ReglaNegocioException("El saldo cambió después de la propuesta; genere una nueva refinanciación");
        PlanPago nuevo = planPagoService.crearPlanRefinanciado(
                contexto.empresaId(), r.getContratoId(), saldoActual, r.getTasaNueva(), actual.getTasaMoraEfectivaAnual(),
                r.getNumeroCuotasNuevo(), Periodicidad.MENSUAL, r.getFechaPrimeraCuota()
        );
        r.aplicar(nuevo.getId());
        return respuestaRefinanciacion(r);
    }

    @Transactional
    public DocumentoCarteraResponse generarEstadoCuenta(ContextoUsuario contexto, DocumentoCarteraRequest request) {
        alcanceService.contrato(contexto, request.contratoId());
        exigirFechaActual(request.fecha());
        validarArchivo(request.archivoId(), contexto.empresaId());
        Saldos saldos = saldos(request.contratoId());
        EstadoCuentaGenerado generado = new EstadoCuentaGenerado(
                contexto.empresaId(), request.contratoId(), request.fecha(), saldos.capital(), saldos.mora(), request.archivoId(), contexto.usuarioId()
        );
        generado.identificar(secuencias.siguiente(SECUENCIA_ESTADOS_CUENTA));
        generado = estadoCuentaRepository.insertar(generado);
        return new DocumentoCarteraResponse(generado.getId(), "ESTADO_CUENTA", null, request.contratoId(), request.fecha(), saldos.capital(), saldos.mora(), saldos.total(), request.archivoId());
    }

    @Transactional
    public DocumentoCarteraResponse generarPazSalvo(ContextoUsuario contexto, DocumentoCarteraRequest request) {
        alcanceService.contratoParaActualizar(contexto, request.contratoId());
        exigirFechaActual(request.fecha());
        validarArchivo(request.archivoId(), contexto.empresaId());
        Saldos saldos = saldos(request.contratoId());
        if (saldos.total().signum() != 0) throw new ReglaNegocioException("El contrato aún tiene obligaciones pendientes");
        PazSalvo pazSalvo = pazSalvoRepository.save(new PazSalvo(
                contexto.empresaId(), generarCodigo("PAZ"), request.contratoId(), request.fecha(), request.archivoId(), contexto.usuarioId()
        ));
        return new DocumentoCarteraResponse(pazSalvo.getId(), "PAZ_SALVO", pazSalvo.getCodigo(), request.contratoId(), request.fecha(), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), request.archivoId());
    }

    @Transactional
    public CierreCarteraResponse cerrarPeriodo(ContextoUsuario contexto, String periodo) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
        YearMonth mes = YearMonth.parse(periodo);
        LocalDate corte = mes.atEndOfMonth();
        if (!corte.isBefore(LocalDate.now(BOGOTA))) throw new ReglaNegocioException("Solo puede cerrarse un periodo ya finalizado");
        LocalDateTime inicioPeriodoPosterior = mes.plusMonths(1).atDay(1).atStartOfDay();
        Long movimientosPosteriores = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM pagos
                   WHERE empresa_id = ? AND creado_en >= ?)
                  +
                  (SELECT COUNT(*) FROM planes_pago
                   WHERE empresa_id = ? AND creado_en >= ?)
                """, Long.class,
                contexto.empresaId(), inicioPeriodoPosterior,
                contexto.empresaId(), inicioPeriodoPosterior);
        if (movimientosPosteriores != null && movimientosPosteriores > 0) {
            throw new ReglaNegocioException("El periodo debió cerrarse antes de registrar movimientos posteriores");
        }
        Long moraPosterior = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cuotas WHERE empresa_id = ? AND mora_calculada_hasta > ?",
                Long.class,
                contexto.empresaId(),
                corte
        );
        if (moraPosterior != null && moraPosterior > 0) {
            throw new ReglaNegocioException("La mora ya fue calculada después del corte; el cierre histórico requiere revisión contable");
        }
        actualizarMoraEmpresa(contexto.empresaId(), corte);
        BigDecimal saldoTotal = dinero(valorConsulta("SELECT COALESCE(SUM(saldo + interes_mora),0) FROM cuotas WHERE empresa_id = ? AND estado IN ('PENDIENTE','PARCIAL','VENCIDA')", contexto.empresaId()));
        BigDecimal saldoVencido = dinero(valorConsulta("SELECT COALESCE(SUM(saldo + interes_mora),0) FROM cuotas WHERE empresa_id = ? AND fecha_vencimiento <= ? AND saldo > 0 AND estado IN ('PENDIENTE','PARCIAL','VENCIDA')", contexto.empresaId(), corte));
        BigDecimal recaudo = dinero(valorConsulta("SELECT COALESCE(SUM(valor),0) FROM pagos WHERE empresa_id = ? AND estado = 'CONFIRMADO' AND fecha_pago >= ? AND fecha_pago < ?", contexto.empresaId(), mes.atDay(1).atStartOfDay(), mes.plusMonths(1).atDay(1).atStartOfDay()));
        BigDecimal indice = saldoTotal.signum() == 0 ? BigDecimal.ZERO.setScale(4) : saldoVencido.divide(saldoTotal, 4, RoundingMode.HALF_UP);
        ObjectNode datos = objectMapper.createObjectNode();
        datos.put("periodo", periodo); datos.put("fechaCorte", corte.toString()); datos.put("saldoTotal", saldoTotal); datos.put("saldoVencido", saldoVencido); datos.put("recaudoPeriodo", recaudo); datos.put("indiceMora", indice);
        LocalDateTime fecha = LocalDateTime.now(BOGOTA);
        try {
            jdbcTemplate.update("INSERT INTO cierres_cartera (empresa_id, periodo, saldo_total, saldo_vencido, recaudo_periodo, indice_mora, datos_json, cerrado_por, cerrado_en) VALUES (?,?,?,?,?,?,?,?,?)",
                    contexto.empresaId(), periodo, saldoTotal, saldoVencido, recaudo, indice, objectMapper.writeValueAsString(datos), contexto.usuarioId(), fecha);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el cierre", e);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("El periodo ya fue cerrado");
        }
        Long id = jdbcTemplate.queryForObject("SELECT id FROM cierres_cartera WHERE empresa_id = ? AND periodo = ?", Long.class, contexto.empresaId(), periodo);
        return new CierreCarteraResponse(id, periodo, saldoTotal, saldoVencido, recaudo, indice, fecha);
    }

    private Refinanciacion obtenerRefinanciacion(ContextoUsuario contexto, Long id) {
        Refinanciacion r = refinanciacionRepository.findForUpdate(id, contexto.empresaId()).orElseThrow(() -> new RecursoNoEncontradoException("La refinanciación no existe"));
        alcanceService.contrato(contexto, r.getContratoId());
        return r;
    }

    private RefinanciacionResponse respuestaRefinanciacion(Refinanciacion r) {
        return RefinanciacionResponse.from(r, refinanciacionCuotaRepository.findByRefinanciacionIdOrderByNumeroAsc(r.getId()));
    }

    private Saldos saldos(Long contratoId) {
        List<Cuota> cuotas = cuotaRepository.findByContratoIdAndEstadoIn(contratoId, ESTADOS_MORA);
        BigDecimal capital = dinero(cuotas.stream().map(Cuota::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal mora = dinero(cuotas.stream().map(Cuota::getInteresMora).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Saldos(capital, mora, capital.add(mora));
    }

    private BigDecimal valorConsulta(String sql, Object... args) {
        BigDecimal valor = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private List<Long> contratosAutorizados(ContextoUsuario contexto) {
        return jdbcTemplate.queryForList("""
                SELECT c.id
                FROM contratos c
                WHERE c.empresa_id = ? AND (
                  EXISTS (SELECT 1 FROM usuario_alcances ua
                    WHERE ua.usuario_id = ? AND ua.empresa_id = ?
                      AND ua.tipo_alcance = 'EMPRESA'
                      AND ua.vigente_desde <= CURRENT_TIMESTAMP(6)
                      AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6)))
                  OR EXISTS (SELECT 1 FROM usuario_alcances ua
                    WHERE ua.usuario_id = ? AND ua.empresa_id = ?
                      AND ua.tipo_alcance = 'PROYECTO'
                      AND ua.proyecto_id = c.proyecto_id
                      AND ua.vigente_desde <= CURRENT_TIMESTAMP(6)
                      AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6)))
                  OR (c.asesor_id = ? AND EXISTS (SELECT 1 FROM usuario_alcances ua
                    WHERE ua.usuario_id = ? AND ua.empresa_id = ?
                      AND ua.tipo_alcance = 'PROPIOS'
                      AND ua.vigente_desde <= CURRENT_TIMESTAMP(6)
                      AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6))))
                )
                """, Long.class,
                contexto.empresaId(), contexto.usuarioId(), contexto.empresaId(),
                contexto.usuarioId(), contexto.empresaId(), contexto.usuarioId(),
                contexto.usuarioId(), contexto.empresaId());
    }

    private void validarArchivo(Long archivoId, Long empresaId) {
        Long cantidad = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archivos WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'", Long.class, archivoId, empresaId);
        if (cantidad == null || cantidad == 0) throw new ReglaNegocioException("El archivo no pertenece a la empresa");
    }

    private static void exigirFechaActual(LocalDate fecha) {
        if (!LocalDate.now(BOGOTA).equals(fecha)) {
            throw new ReglaNegocioException("El documento debe generarse con la fecha actual para conservar saldos verificables");
        }
    }

    private static String generarCodigo(String prefijo) { return prefijo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT); }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private static BigDecimal dinero(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
    private record Saldos(BigDecimal capital, BigDecimal mora, BigDecimal total) { }
}
