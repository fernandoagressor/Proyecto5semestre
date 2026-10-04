package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.CuotaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PlanPagoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PlanPagoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConfiguracionCartera;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.OrigenPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoContrato;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.CuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PlanPagoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanPagoService {
    private final PlanPagoRepository planRepository;
    private final CuotaRepository cuotaRepository;
    private final CarteraAlcanceService alcanceService;
    private final CuotaCalculadora calculadora;
    private final ConfiguracionCarteraService configuracionService;

    public PlanPagoService(
            PlanPagoRepository planRepository,
            CuotaRepository cuotaRepository,
            CarteraAlcanceService alcanceService,
            CuotaCalculadora calculadora,
            ConfiguracionCarteraService configuracionService
    ) {
        this.planRepository = planRepository;
        this.cuotaRepository = cuotaRepository;
        this.alcanceService = alcanceService;
        this.calculadora = calculadora;
        this.configuracionService = configuracionService;
    }

    @Transactional(readOnly = true)
    public List<PlanPagoResponse> listar(ContextoUsuario contexto, Long contratoId) {
        alcanceService.contrato(contexto, contratoId);
        return planRepository.findByContratoIdAndEmpresaIdOrderByVersionPlanDesc(contratoId, contexto.empresaId())
                .stream().map(this::respuesta).toList();
    }

    @Transactional
    public PlanPagoResponse crear(ContextoUsuario contexto, PlanPagoCrearRequest request) {
        Contrato contrato = alcanceService.contratoParaActualizar(contexto, request.contratoId());
        exigirVigente(contrato);
        if (contrato.getSaldoFinanciado().signum() <= 0) {
            throw new ReglaNegocioException("El contrato no tiene saldo financiado para programar");
        }
        if (request.numeroCuotas() != contrato.getNumeroCuotas()) {
            throw new ReglaNegocioException("El número de cuotas debe coincidir con el contrato vigente");
        }
        if (planRepository.findByContratoIdAndEmpresaIdAndEstado(contrato.getId(), contexto.empresaId(), EstadoPlanPago.ACTIVO).isPresent()) {
            throw new ConflictoException("El contrato ya tiene un plan activo; use refinanciación para reemplazarlo");
        }
        PlanPago plan = crearPlan(
                contexto.empresaId(), contrato.getId(), dinero(contrato.getSaldoFinanciado()),
                request.tasaEfectivaAnual(), request.tasaMoraEfectivaAnual(), request.numeroCuotas(),
                request.periodicidad(), request.fechaPrimeraCuota(), OrigenPlanPago.CONTRATO
        );
        return respuesta(plan);
    }

    @Transactional
    public PlanPagoResponse activar(ContextoUsuario contexto, Long planId) {
        PlanPago plan = planRepository.findForUpdate(planId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El plan de pago no existe"));
        Contrato contrato = alcanceService.contratoParaActualizar(contexto, plan.getContratoId());
        exigirVigente(contrato);
        planRepository.findActivoForUpdate(contrato.getId(), contexto.empresaId(), EstadoPlanPago.ACTIVO)
                .filter(actual -> !actual.getId().equals(planId))
                .ifPresent(actual -> { throw new ConflictoException("El contrato ya tiene otro plan activo"); });
        plan.activar();
        return respuesta(plan);
    }

    @Transactional
    public PlanPagoResponse sincronizarContrato(ContextoUsuario contexto, Long contratoId) {
        Contrato contrato = alcanceService.contratoParaActualizar(contexto, contratoId);
        exigirVigente(contrato);
        PlanPago existente = planRepository
                .findByContratoIdAndEmpresaIdAndEstado(
                        contratoId,
                        contexto.empresaId(),
                        EstadoPlanPago.ACTIVO
                )
                .orElse(null);
        if (existente != null) {
            return respuesta(existente);
        }
        return respuesta(crearPlanAutomatico(contrato));
    }

    @Transactional
    public void generarAutomatico(ContextoUsuario contexto, Contrato contrato) {
        ConfiguracionCartera configuracion = configuracionService
                .obtenerEntidad(contexto.empresaId());
        if (!configuracion.isAutoGenerarPlan()
                || contrato.getSaldoFinanciado().signum() <= 0
                || planRepository.findByContratoIdAndEmpresaIdAndEstado(
                        contrato.getId(),
                        contexto.empresaId(),
                        EstadoPlanPago.ACTIVO
                ).isPresent()) {
            return;
        }
        crearPlanAutomatico(contrato, configuracion);
    }

    @Transactional
    public PlanPago crearPlanRefinanciado(Long empresaId, Long contratoId, BigDecimal saldo, BigDecimal tasa, BigDecimal tasaMora, int cuotas, Periodicidad periodicidad, LocalDate primeraFecha) {
        PlanPago anterior = planRepository.findActivoForUpdate(contratoId, empresaId, EstadoPlanPago.ACTIVO)
                .orElseThrow(() -> new ReglaNegocioException("El contrato no tiene un plan activo para refinanciar"));
        anterior.reemplazar();
        cuotaRepository.findByPlanPagoIdOrderByNumeroAsc(anterior.getId()).forEach(Cuota::cancelarPendiente);
        PlanPago nuevo = crearPlan(empresaId, contratoId, saldo, tasa, tasaMora, cuotas, periodicidad, primeraFecha, OrigenPlanPago.REFINANCIACION);
        nuevo.activar();
        return nuevo;
    }

    private PlanPago crearPlan(Long empresaId, Long contratoId, BigDecimal saldo, BigDecimal tasa, BigDecimal tasaMora, int numeroCuotas, Periodicidad periodicidad, LocalDate primeraFecha, OrigenPlanPago origen) {
        int version = planRepository.maxVersion(contratoId) + 1;
        BigDecimal capitalEnPesos = pesos(saldo);
        PlanPago plan = planRepository.saveAndFlush(new PlanPago(
                empresaId, contratoId, version, capitalEnPesos, tasa, tasaMora,
                numeroCuotas, periodicidad, primeraFecha, origen
        ));
        List<Cuota> cuotas = calculadora.generar(capitalEnPesos, numeroCuotas, tasa, periodicidad, primeraFecha)
                .stream().map(c -> new Cuota(
                        empresaId, plan.getId(), contratoId, c.numero(), c.fechaVencimiento(),
                        c.capital(), c.interes(), BigDecimal.ZERO.setScale(2)
                )).toList();
        cuotaRepository.saveAll(cuotas);
        return plan;
    }

    public BigDecimal saldoContrato(Long contratoId) {
        return cuotaRepository.findByContratoIdAndEstadoIn(contratoId, List.of(EstadoCuota.PENDIENTE, EstadoCuota.PARCIAL, EstadoCuota.VENCIDA))
                .stream().map(c -> c.getSaldo().add(c.getInteresMora())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private PlanPago crearPlanAutomatico(Contrato contrato) {
        return crearPlanAutomatico(
                contrato,
                configuracionService.obtenerEntidad(contrato.getEmpresaId())
        );
    }

    private PlanPago crearPlanAutomatico(
            Contrato contrato,
            ConfiguracionCartera configuracion
    ) {
        if (contrato.getSaldoFinanciado().signum() <= 0) {
            throw new ReglaNegocioException(
                    "El contrato no tiene saldo financiado para generar cartera"
            );
        }
        LocalDate base = contrato.getFechaFirma() == null
                ? LocalDate.now()
                : contrato.getFechaFirma();
        if (contrato.getFechaInicio() != null
                && contrato.getFechaInicio().isAfter(base)) {
            base = contrato.getFechaInicio();
        }
        PlanPago plan = crearPlan(
                contrato.getEmpresaId(),
                contrato.getId(),
                dinero(contrato.getSaldoFinanciado()),
                configuracion.getTasaEfectivaAnual(),
                configuracion.getTasaMoraEfectivaAnual(),
                contrato.getNumeroCuotas(),
                configuracion.getPeriodicidad(),
                base.plusDays(configuracion.getDiasPrimeraCuota()),
                OrigenPlanPago.CONTRATO
        );
        plan.activar();
        return plan;
    }

    private PlanPagoResponse respuesta(PlanPago plan) {
        return PlanPagoResponse.from(plan, cuotaRepository.findByPlanPagoIdOrderByNumeroAsc(plan.getId()).stream().map(CuotaResponse::from).toList());
    }

    private static void exigirVigente(Contrato contrato) {
        if (contrato.getEstado() != EstadoContrato.VIGENTE) throw new ReglaNegocioException("El plan solo puede programarse para un contrato vigente");
    }

    private static BigDecimal dinero(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }

    private static BigDecimal pesos(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }
}
