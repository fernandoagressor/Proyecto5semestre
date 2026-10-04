package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConciliacionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConciliacionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MovimientoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MovimientoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionBancaria;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionMovimiento;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoConciliacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoMovimientoConciliacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Pago;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.ConciliacionMovimientoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.ConciliacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PagoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConciliacionService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ConciliacionRepository conciliacionRepository;
    private final ConciliacionMovimientoRepository movimientoRepository;
    private final PagoRepository pagoRepository;
    private final CarteraAlcanceService carteraAlcanceService;
    private final AlcanceProyectoService alcanceProyectoService;

    public ConciliacionService(ConciliacionRepository conciliacionRepository, ConciliacionMovimientoRepository movimientoRepository, PagoRepository pagoRepository, CarteraAlcanceService carteraAlcanceService, AlcanceProyectoService alcanceProyectoService) {
        this.conciliacionRepository = conciliacionRepository;
        this.movimientoRepository = movimientoRepository;
        this.pagoRepository = pagoRepository;
        this.carteraAlcanceService = carteraAlcanceService;
        this.alcanceProyectoService = alcanceProyectoService;
    }

    @Transactional(readOnly = true)
    public List<ConciliacionResponse> listar(ContextoUsuario contexto) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
        return conciliacionRepository.findByEmpresaIdOrderByCreadoEnDesc(contexto.empresaId()).stream().map(this::respuesta).toList();
    }

    @Transactional
    public ConciliacionResponse crear(ContextoUsuario contexto, ConciliacionCrearRequest request) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
        ConciliacionBancaria conciliacion = conciliacionRepository.save(new ConciliacionBancaria(
                contexto.empresaId(), generarCodigo(), request.cuentaBancaria().trim(), request.fechaDesde(), request.fechaHasta(), dinero(request.saldoExtracto()), contexto.usuarioId()
        ));
        return respuesta(conciliacion);
    }

    @Transactional
    public MovimientoResponse agregarMovimiento(ContextoUsuario contexto, Long conciliacionId, MovimientoCrearRequest request) {
        ConciliacionBancaria conciliacion = obtenerParaActualizar(contexto, conciliacionId);
        if (conciliacion.getEstado() == EstadoConciliacion.CERRADA || conciliacion.getEstado() == EstadoConciliacion.ANULADA) throw new ReglaNegocioException("La conciliación está cerrada");
        if (request.fechaMovimiento().isBefore(conciliacion.getFechaDesde()) || request.fechaMovimiento().isAfter(conciliacion.getFechaHasta())) throw new ReglaNegocioException("El movimiento está fuera del periodo del extracto");
        if (conciliacion.getEstado() == EstadoConciliacion.BORRADOR) conciliacion.iniciar();
        return MovimientoResponse.from(movimientoRepository.save(new ConciliacionMovimiento(
                conciliacionId, request.fechaMovimiento(), request.referencia().trim(), normalizar(request.descripcion()), dinero(request.valor())
        )));
    }

    @Transactional
    public MovimientoResponse conciliarMovimiento(ContextoUsuario contexto, Long conciliacionId, Long movimientoId, Long pagoId) {
        ConciliacionBancaria conciliacion = obtenerParaActualizar(contexto, conciliacionId);
        if (conciliacion.getEstado() == EstadoConciliacion.CERRADA || conciliacion.getEstado() == EstadoConciliacion.ANULADA) throw new ReglaNegocioException("La conciliación está cerrada");
        ConciliacionMovimiento movimiento = movimientoRepository.findForUpdate(movimientoId, conciliacionId).orElseThrow(() -> new RecursoNoEncontradoException("El movimiento no existe"));
        Pago pago = pagoRepository.findForUpdate(pagoId, contexto.empresaId()).orElseThrow(() -> new ReglaNegocioException("El pago no existe en la empresa"));
        if (pago.getEstado() != EstadoPago.CONFIRMADO) throw new ReglaNegocioException("Solo se concilian pagos confirmados");
        if (pago.getFechaPago().toLocalDate().isBefore(conciliacion.getFechaDesde())
                || pago.getFechaPago().toLocalDate().isAfter(conciliacion.getFechaHasta())) {
            throw new ReglaNegocioException("El pago está fuera del periodo de conciliación");
        }
        if (pago.getContratoId() != null) carteraAlcanceService.contrato(contexto, pago.getContratoId());
        movimiento.conciliar(pago);
        return MovimientoResponse.from(movimiento);
    }

    @Transactional
    public MovimientoResponse ignorarMovimiento(ContextoUsuario contexto, Long conciliacionId, Long movimientoId) {
        obtenerParaActualizar(contexto, conciliacionId);
        ConciliacionMovimiento movimiento = movimientoRepository.findForUpdate(movimientoId, conciliacionId).orElseThrow(() -> new RecursoNoEncontradoException("El movimiento no existe"));
        movimiento.ignorar();
        return MovimientoResponse.from(movimiento);
    }

    @Transactional
    public ConciliacionResponse cerrar(ContextoUsuario contexto, Long conciliacionId) {
        ConciliacionBancaria conciliacion = obtenerParaActualizar(contexto, conciliacionId);
        long pendientes = movimientoRepository.countByConciliacionIdAndEstadoIn(conciliacionId, List.of(EstadoMovimientoConciliacion.PENDIENTE));
        if (pendientes > 0) throw new ReglaNegocioException("Todos los movimientos deben conciliarse o ignorarse antes del cierre");
        BigDecimal total = movimientoRepository.totalPorEstado(conciliacionId, EstadoMovimientoConciliacion.CONCILIADO);
        conciliacion.cerrar(dinero(total), LocalDateTime.now(BOGOTA));
        return respuesta(conciliacion);
    }

    private ConciliacionBancaria obtenerParaActualizar(ContextoUsuario contexto, Long id) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
        return conciliacionRepository.findForUpdate(id, contexto.empresaId()).orElseThrow(() -> new RecursoNoEncontradoException("La conciliación no existe"));
    }

    private ConciliacionResponse respuesta(ConciliacionBancaria c) {
        return ConciliacionResponse.from(c, movimientoRepository.findByConciliacionIdOrderByFechaMovimientoAscIdAsc(c.getId()).stream().map(MovimientoResponse::from).toList());
    }

    private static String generarCodigo() { return "CONC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT); }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private static BigDecimal dinero(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
}
