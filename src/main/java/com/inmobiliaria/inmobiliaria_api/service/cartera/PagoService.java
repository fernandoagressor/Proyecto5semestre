package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoAplicacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoAplicacionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoConfirmarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ReversionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ReversionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConceptoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Factura;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.MetodoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Pago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoAplicacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoHistorialEstado;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoReversion;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoPagoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.CuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.FacturaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.MetodoPagoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PagoAplicacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PagoHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PagoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PagoReversionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.ventas.reserva.ReservaService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PagoService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final PagoRepository pagoRepository;
    private final PagoAplicacionRepository aplicacionRepository;
    private final PagoHistorialRepository historialRepository;
    private final PagoReversionRepository reversionRepository;
    private final CuotaRepository cuotaRepository;
    private final FacturaRepository facturaRepository;
    private final MetodoPagoRepository metodoRepository;
    private final ClienteRepository clienteRepository;
    private final CarteraAlcanceService alcanceService;
    private final ReservaService reservaService;
    private final JdbcTemplate jdbcTemplate;

    public PagoService(PagoRepository pagoRepository, PagoAplicacionRepository aplicacionRepository, PagoHistorialRepository historialRepository, PagoReversionRepository reversionRepository, CuotaRepository cuotaRepository, FacturaRepository facturaRepository, MetodoPagoRepository metodoRepository, ClienteRepository clienteRepository, CarteraAlcanceService alcanceService, ReservaService reservaService, JdbcTemplate jdbcTemplate) {
        this.pagoRepository = pagoRepository;
        this.aplicacionRepository = aplicacionRepository;
        this.historialRepository = historialRepository;
        this.reversionRepository = reversionRepository;
        this.cuotaRepository = cuotaRepository;
        this.facturaRepository = facturaRepository;
        this.metodoRepository = metodoRepository;
        this.clienteRepository = clienteRepository;
        this.alcanceService = alcanceService;
        this.reservaService = reservaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PagoResponse> listar(ContextoUsuario contexto, EstadoPago estado, Long contratoId, Long clienteId, int pagina, int tamano) {
        Specification<Pago> filtro = (root, query, cb) -> cb.equal(root.get("empresaId"), contexto.empresaId());
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (contratoId != null) {
            alcanceService.contrato(contexto, contratoId);
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("contratoId"), contratoId));
        } else {
            List<Long> pagos = pagosAutorizados(contexto);
            filtro = filtro.and((root, query, cb) ->
                    pagos.isEmpty() ? cb.disjunction() : root.get("id").in(pagos));
        }
        if (clienteId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        Page<Pago> resultado = pagoRepository.findAll(filtro, PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamano), 100), Sort.by(Sort.Direction.DESC, "fechaPago")));
        return PaginaResponse.from(resultado, this::respuesta);
    }

    @Transactional
    public PagoResponse registrar(ContextoUsuario contexto, PagoCrearRequest request) {
        validarPeriodoAbierto(contexto.empresaId(), request.fechaPago().toLocalDate());
        validarDestino(contexto, request);
        MetodoPago metodo = metodoRepository.findDisponible(request.metodoPagoId(), contexto.empresaId())
                .orElseThrow(() -> new ReglaNegocioException("El método de pago no está disponible"));
        String referencia = normalizar(request.referencia());
        if (metodo.isRequiereReferencia() && referencia == null) throw new ReglaNegocioException("El método exige referencia");
        if (metodo.isRequiereComprobante() && request.comprobanteId() == null) throw new ReglaNegocioException("El método exige comprobante");
        if (referencia != null && pagoRepository.existsByEmpresaIdAndReferencia(contexto.empresaId(), referencia)) throw new ConflictoException("La referencia del pago ya fue registrada");
        validarArchivo(request.comprobanteId(), contexto.empresaId());
        Pago pago = pagoRepository.save(new Pago(
                contexto.empresaId(), generarCodigo(), request.contratoId(), request.reservaId(),
                request.clienteId(), request.metodoPagoId(), request.fechaPago(), dinero(request.valor()),
                referencia, request.comprobanteId(), request.origen(), contexto.usuarioId(), normalizar(request.observaciones())
        ));
        historialRepository.save(new PagoHistorialEstado(pago.getId(), null, pago.getEstado(), "Pago registrado", contexto.usuarioId()));
        return respuesta(pago);
    }

    @Transactional
    public PagoResponse enviarRevision(ContextoUsuario contexto, Long pagoId) {
        Pago pago = obtenerParaActualizar(contexto, pagoId);
        EstadoPago anterior = pago.enviarRevision();
        guardarHistorial(pago, anterior, "Pago enviado a revisión", contexto.usuarioId());
        return respuesta(pago);
    }

    @Transactional
    public PagoResponse confirmar(ContextoUsuario contexto, Long pagoId, PagoConfirmarRequest request) {
        Pago pago = obtenerParaActualizar(contexto, pagoId);
        if (!aplicacionRepository.findByPagoIdOrderByIdAsc(pagoId).isEmpty()) throw new ConflictoException("El pago ya tiene aplicaciones");
        BigDecimal total = request.aplicaciones().stream().map(PagoAplicacionRequest::valor).map(PagoService::dinero).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(pago.getValor()) != 0) throw new ReglaNegocioException("Las aplicaciones deben sumar exactamente el valor del pago");
        LocalDate fecha = ahora().toLocalDate();
        List<PagoAplicacion> aplicaciones = request.aplicaciones().stream()
                .map(item -> aplicar(contexto, pago, item, fecha)).toList();
        aplicacionRepository.saveAll(aplicaciones);
        if (pago.getReservaId() != null) {
            Reserva reserva = reservaService.obtenerAutorizada(contexto, pago.getReservaId());
            if (reserva.getEstadoPago() == EstadoPagoReserva.PENDIENTE) reservaService.confirmarPago(contexto, reserva.getId(), pago.getComprobanteId());
        }
        EstadoPago anterior = pago.confirmar(contexto.usuarioId(), ahora());
        guardarHistorial(pago, anterior, normalizar(request.motivo()) == null ? "Pago confirmado y aplicado" : request.motivo().trim(), contexto.usuarioId());
        return respuesta(pago);
    }

    @Transactional
    public PagoResponse rechazar(ContextoUsuario contexto, Long pagoId, String motivo) {
        Pago pago = obtenerParaActualizar(contexto, pagoId);
        EstadoPago anterior = pago.rechazar(contexto.usuarioId(), ahora(), motivo);
        if (pago.getReservaId() != null) {
            Reserva reserva = reservaService.obtenerAutorizada(contexto, pago.getReservaId());
            if (reserva.getEstadoPago() == EstadoPagoReserva.PENDIENTE) reservaService.rechazarPago(contexto, reserva.getId(), motivo);
        }
        guardarHistorial(pago, anterior, motivo.trim(), contexto.usuarioId());
        return respuesta(pago);
    }

    /** Solicitudes de reversión de la empresa (para aprobarlas o rechazarlas desde Cartera). */
    @Transactional(readOnly = true)
    public List<ReversionResponse> reversiones(ContextoUsuario contexto, com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoReversion estado) {
        return reversionRepository.findDeEmpresa(contexto.empresaId(), estado, PageRequest.of(0, 100)).stream()
                .map(ReversionResponse::from)
                .toList();
    }

    @Transactional
    public ReversionResponse solicitarReversion(ContextoUsuario contexto, Long pagoId, ReversionCrearRequest request) {
        Pago pago = obtenerParaActualizar(contexto, pagoId);
        if (pago.getEstado() != EstadoPago.CONFIRMADO) throw new ReglaNegocioException("Solo un pago confirmado puede reversarse");
        validarArchivo(request.evidenciaId(), contexto.empresaId());
        return ReversionResponse.from(reversionRepository.save(new PagoReversion(
                pago.getId(), pago.getValor(), request.motivo().trim(), request.evidenciaId(), contexto.usuarioId()
        )));
    }

    @Transactional
    public PagoResponse aprobarReversion(ContextoUsuario contexto, Long pagoId, Long reversionId) {
        Pago pago = obtenerParaActualizar(contexto, pagoId);
        PagoReversion reversion = reversionRepository.findForUpdate(reversionId, pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud de reversión no existe"));
        LocalDate fecha = ahora().toLocalDate();
        List<PagoAplicacion> aplicaciones = aplicacionRepository.findByPagoIdOrderByIdAsc(pagoId);
        for (PagoAplicacion aplicacion : aplicaciones) revertirAplicacion(contexto, pago, aplicacion, fecha);
        if (pago.getReservaId() != null) {
            Reserva reserva = reservaService.obtenerAutorizada(contexto, pago.getReservaId());
            if (reserva.getEstado() == com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva.CONVERTIDA) {
                throw new ReglaNegocioException("El pago pertenece a una reserva convertida; anule primero el contrato relacionado");
            }
            if (reserva.getEstadoPago() == EstadoPagoReserva.CONFIRMADO) {
                reservaService.reversarPago(contexto, reserva.getId(), reversion.getMotivo());
            }
        }
        reversion.aprobarYEjecutar(contexto.usuarioId(), ahora());
        EstadoPago anterior = pago.reversar();
        guardarHistorial(pago, anterior, "Pago reversado: " + reversion.getMotivo(), contexto.usuarioId());
        return respuesta(pago);
    }

    @Transactional
    public ReversionResponse rechazarReversion(ContextoUsuario contexto, Long pagoId, Long reversionId) {
        obtenerParaActualizar(contexto, pagoId);
        PagoReversion reversion = reversionRepository.findForUpdate(reversionId, pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud de reversión no existe"));
        reversion.rechazar(contexto.usuarioId());
        return ReversionResponse.from(reversion);
    }

    private PagoAplicacion aplicar(ContextoUsuario contexto, Pago pago, PagoAplicacionRequest request, LocalDate fecha) {
        validarDestinoAplicacion(pago, request);
        BigDecimal valor = dinero(request.valor());
        if (request.cuotaId() != null) {
            Cuota cuota = cuotaRepository.findForUpdate(request.cuotaId(), contexto.empresaId())
                    .orElseThrow(() -> new ReglaNegocioException("La cuota no existe en la empresa"));
            exigirContrato(pago, cuota.getContratoId());
            cuota.aplicar(valor, request.concepto(), fecha);
        }
        if (request.facturaId() != null) {
            Factura factura = facturaRepository.findForUpdate(request.facturaId(), contexto.empresaId())
                    .orElseThrow(() -> new ReglaNegocioException("La factura no existe en la empresa"));
            exigirContrato(pago, factura.getContratoId());
            factura.aplicar(valor, fecha);
        }
        return new PagoAplicacion(pago.getId(), request.cuotaId(), request.facturaId(), request.concepto(), valor);
    }

    private void revertirAplicacion(ContextoUsuario contexto, Pago pago, PagoAplicacion aplicacion, LocalDate fecha) {
        if (aplicacion.getCuotaId() != null) {
            Cuota cuota = cuotaRepository.findForUpdate(aplicacion.getCuotaId(), contexto.empresaId()).orElseThrow(() -> new ReglaNegocioException("La cuota aplicada ya no existe"));
            exigirContrato(pago, cuota.getContratoId());
            cuota.revertir(aplicacion.getValorAplicado(), aplicacion.getConcepto(), fecha);
        }
        if (aplicacion.getFacturaId() != null) {
            Factura factura = facturaRepository.findForUpdate(aplicacion.getFacturaId(), contexto.empresaId()).orElseThrow(() -> new ReglaNegocioException("La factura aplicada ya no existe"));
            factura.revertir(aplicacion.getValorAplicado(), fecha);
        }
    }

    private void validarDestino(ContextoUsuario contexto, PagoCrearRequest request) {
        if (request.contratoId() == null && request.reservaId() == null) throw new ReglaNegocioException("Debe indicar contrato o reserva");
        clienteRepository.findByIdAndEmpresaIdAndEstado(request.clienteId(), contexto.empresaId(), "ACTIVO")
                .orElseThrow(() -> new ReglaNegocioException("El cliente no está activo en la empresa"));
        Contrato contratoDestino = null;
        if (request.contratoId() != null) {
            contratoDestino = alcanceService.contrato(contexto, request.contratoId());
            Long vinculaciones = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contrato_clientes WHERE contrato_id = ? AND cliente_id = ?", Long.class, contratoDestino.getId(), request.clienteId());
            if (vinculaciones == null || vinculaciones == 0) throw new ReglaNegocioException("El cliente no pertenece al contrato");
        }
        if (request.reservaId() != null) {
            Reserva reserva = reservaService.obtenerAutorizada(contexto, request.reservaId());
            if (!reserva.getClienteId().equals(request.clienteId())) throw new ReglaNegocioException("El cliente no pertenece a la reserva");
            if (reserva.getEstadoPago() != EstadoPagoReserva.PENDIENTE) {
                throw new ReglaNegocioException("La reserva ya no tiene un pago pendiente");
            }
            if (contratoDestino != null && !reserva.getId().equals(contratoDestino.getReservaId())) {
                throw new ReglaNegocioException("La reserva y el contrato no corresponden");
            }
        }
    }

    private void validarDestinoAplicacion(Pago pago, PagoAplicacionRequest request) {
        if (request.concepto() == ConceptoPago.RESERVA
                && (pago.getReservaId() == null
                || request.cuotaId() != null
                || request.facturaId() != null)) {
            throw new ReglaNegocioException("El concepto reserva solo aplica al pago directo de una reserva");
        }
        if (request.cuotaId() == null && request.facturaId() == null && request.concepto() != ConceptoPago.RESERVA && request.concepto() != ConceptoPago.INICIAL && request.concepto() != ConceptoPago.OTRO) {
            throw new ReglaNegocioException("La aplicación requiere cuota o factura");
        }
    }

    private Pago obtenerParaActualizar(ContextoUsuario contexto, Long pagoId) {
        Pago pago = pagoRepository.findForUpdate(pagoId, contexto.empresaId()).orElseThrow(() -> new RecursoNoEncontradoException("El pago no existe"));
        if (pago.getContratoId() != null) alcanceService.contrato(contexto, pago.getContratoId());
        else reservaService.obtenerAutorizada(contexto, pago.getReservaId());
        return pago;
    }

    private void exigirContrato(Pago pago, Long contratoId) {
        if (pago.getContratoId() == null || !pago.getContratoId().equals(contratoId)) throw new ReglaNegocioException("La aplicación no pertenece al contrato del pago");
    }

    private void guardarHistorial(Pago pago, EstadoPago anterior, String motivo, Long usuarioId) {
        historialRepository.save(new PagoHistorialEstado(pago.getId(), anterior, pago.getEstado(), motivo, usuarioId));
    }

    private PagoResponse respuesta(Pago pago) {
        return PagoResponse.from(pago, aplicacionRepository.findByPagoIdOrderByIdAsc(pago.getId()).stream().map(PagoAplicacionResponse::from).toList());
    }

    private List<Long> pagosAutorizados(ContextoUsuario contexto) {
        return jdbcTemplate.queryForList("""
                SELECT p.id
                FROM pagos p
                LEFT JOIN contratos c ON c.id = p.contrato_id
                LEFT JOIN reservas r ON r.id = p.reserva_id
                WHERE p.empresa_id = ? AND (
                  EXISTS (SELECT 1 FROM usuario_alcances ua WHERE ua.usuario_id = ? AND ua.empresa_id = ? AND ua.tipo_alcance = 'EMPRESA' AND ua.vigente_desde <= CURRENT_TIMESTAMP(6) AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6)))
                  OR ((c.asesor_id = ? OR r.asesor_id = ?) AND EXISTS (
                    SELECT 1 FROM usuario_alcances ua
                    WHERE ua.usuario_id = ? AND ua.empresa_id = ?
                      AND ua.tipo_alcance = 'PROPIOS'
                      AND ua.vigente_desde <= CURRENT_TIMESTAMP(6)
                      AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6))
                  ))
                  OR EXISTS (SELECT 1 FROM usuario_alcances ua WHERE ua.usuario_id = ? AND ua.empresa_id = ? AND ua.tipo_alcance = 'PROYECTO' AND ua.proyecto_id IN (c.proyecto_id, r.proyecto_id) AND ua.vigente_desde <= CURRENT_TIMESTAMP(6) AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6)))
                )
                """, Long.class,
                contexto.empresaId(), contexto.usuarioId(), contexto.empresaId(),
                contexto.usuarioId(), contexto.usuarioId(), contexto.usuarioId(), contexto.empresaId(),
                contexto.usuarioId(), contexto.empresaId());
    }

    private void validarPeriodoAbierto(Long empresaId, LocalDate fecha) {
        String periodo = fecha.toString().substring(0, 7);
        Long cantidad = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cierres_cartera WHERE empresa_id = ? AND periodo = ?", Long.class, empresaId, periodo);
        if (cantidad != null && cantidad > 0) throw new ReglaNegocioException("El periodo de cartera ya está cerrado");
    }

    private void validarArchivo(Long archivoId, Long empresaId) {
        if (archivoId == null) return;
        Long cantidad = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archivos WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'", Long.class, archivoId, empresaId);
        if (cantidad == null || cantidad == 0) throw new ReglaNegocioException("El archivo no pertenece a la empresa");
    }

    private static String generarCodigo() { return "PAG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT); }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private static BigDecimal dinero(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
    private static LocalDateTime ahora() { return LocalDateTime.now(BOGOTA); }
}
