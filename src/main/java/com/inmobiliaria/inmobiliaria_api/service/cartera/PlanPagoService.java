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
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoPagoReserva;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva.ReservaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ReservaRepository reservaRepository;

    public PlanPagoService(
            PlanPagoRepository planRepository,
            CuotaRepository cuotaRepository,
            CarteraAlcanceService alcanceService,
            CuotaCalculadora calculadora,
            ConfiguracionCarteraService configuracionService,
            ReservaRepository reservaRepository
    ) {
        this.planRepository = planRepository;
        this.cuotaRepository = cuotaRepository;
        this.alcanceService = alcanceService;
        this.calculadora = calculadora;
        this.configuracionService = configuracionService;
        this.reservaRepository = reservaRepository;
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
        agregarCuotaInicial(plan, contrato, inicialPendiente(contrato), fechaCuotaInicial(contrato));
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
                || (contrato.getSaldoFinanciado().signum() <= 0 && inicialPendiente(contrato).signum() <= 0)
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
        if (capitalEnPesos.signum() > 0) {
            List<Cuota> cuotas = calculadora.generar(capitalEnPesos, numeroCuotas, tasa, periodicidad, primeraFecha)
                    .stream().map(c -> new Cuota(
                            empresaId, plan.getId(), contratoId, c.numero(), c.fechaVencimiento(),
                            c.capital(), c.interes(), BigDecimal.ZERO.setScale(2)
                    )).toList();
            cuotaRepository.saveAll(cuotas);
        }
        return plan;
    }

    /**
     * Cuota 0: lo que falta de la cuota inicial después de descontar lo pagado en la reserva.
     * Vence el día de la firma, sin intereses; si no se paga, entra en mora como cualquier cuota.
     */
    private void agregarCuotaInicial(PlanPago plan, Contrato contrato, BigDecimal pendiente, LocalDate vence) {
        if (pendiente.signum() <= 0) {
            return;
        }
        BigDecimal cero = BigDecimal.ZERO.setScale(2);
        cuotaRepository.save(new Cuota(plan.getEmpresaId(), plan.getId(), contrato.getId(), 0, vence,
                pesos(pendiente), cero, cero));
    }

    /** Lo que se pagó de la reserva (con el pago confirmado) y que abona a la cuota inicial. */
    public BigDecimal abonoDeReserva(Contrato contrato) {
        if (contrato.getReservaId() == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return reservaRepository.findByIdAndEmpresaId(contrato.getReservaId(), contrato.getEmpresaId())
                .filter(reserva -> reserva.getEstadoPago() == EstadoPagoReserva.CONFIRMADO)
                .map(reserva -> dinero(reserva.getValorReserva()))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /** Parte de la cuota inicial que todavía se debe (cuota inicial − lo pagado en la reserva). */
    public BigDecimal inicialPendiente(Contrato contrato) {
        return inicialPendiente(contrato.getCuotaInicial(), abonoDeReserva(contrato));
    }

    public static BigDecimal inicialPendiente(BigDecimal cuotaInicial, BigDecimal abonado) {
        return dinero(cuotaInicial.subtract(abonado).max(BigDecimal.ZERO));
    }

    private static LocalDate fechaCuotaInicial(Contrato contrato) {
        return contrato.getFechaFirma() == null ? LocalDate.now() : contrato.getFechaFirma();
    }

    /**
     * Cronograma que generaría Cartera para estas condiciones, sin guardar nada: cuota 0 (si queda
     * cuota inicial por pagar) y las cuotas del saldo con la tasa y la periodicidad de la empresa.
     */
    @Transactional(readOnly = true)
    public Proyeccion proyectar(Long empresaId, BigDecimal saldo, int numeroCuotas, BigDecimal inicialPendiente,
                                LocalDate fechaFirma, LocalDate fechaInicio) {
        ConfiguracionCartera configuracion = configuracionService.obtenerEntidad(empresaId);
        LocalDate base = fechaInicio != null && fechaInicio.isAfter(fechaFirma) ? fechaInicio : fechaFirma;
        List<CuotaCalculadora.CuotaCalculada> cuotas = new ArrayList<>();
        if (inicialPendiente.signum() > 0) {
            cuotas.add(new CuotaCalculadora.CuotaCalculada(0, fechaFirma, pesos(inicialPendiente),
                    BigDecimal.ZERO.setScale(2)));
        }
        if (pesos(saldo).signum() > 0 && numeroCuotas > 0) {
            cuotas.addAll(calculadora.generar(pesos(saldo), numeroCuotas, configuracion.getTasaEfectivaAnual(),
                    configuracion.getPeriodicidad(), base.plusDays(configuracion.getDiasPrimeraCuota())));
        }
        return new Proyeccion(configuracion.getTasaEfectivaAnual(), configuracion.getPeriodicidad(),
                configuracion.isAutoGenerarPlan(), cuotas);
    }

    public record Proyeccion(BigDecimal tasaEfectivaAnual, Periodicidad periodicidad, boolean automatico,
                             List<CuotaCalculadora.CuotaCalculada> cuotas) {
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
        BigDecimal pendiente = inicialPendiente(contrato);
        if (contrato.getSaldoFinanciado().signum() <= 0 && pendiente.signum() <= 0) {
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
        agregarCuotaInicial(plan, contrato, pendiente, fechaCuotaInicial(contrato));
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
