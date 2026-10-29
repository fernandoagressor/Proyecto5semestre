package com.inmobiliaria.inmobiliaria_api.service.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaHistorialResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.LoteHistorialEstado;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.ReservaHistorial;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteHistorialEstadoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva.ReservaHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva.ReservaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.crm.PendientesCrmService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Set<EstadoReserva> ESTADOS_VIGENTES = Set.of(
            EstadoReserva.PENDIENTE_PAGO,
            EstadoReserva.ACTIVA
    );

    private final ReservaRepository reservaRepository;
    private final ReservaHistorialRepository historialRepository;
    private final ClienteRepository clienteRepository;
    private final OportunidadService oportunidadService;
    private final PendientesCrmService pendientesCrm;
    private final SimulacionRepository simulacionRepository;
    private final LoteRepository loteRepository;
    private final LoteHistorialEstadoRepository loteHistorialRepository;
    private final CrmAlcanceService crmAlcanceService;
    private final JdbcTemplate jdbcTemplate;

    public ReservaService(
            ReservaRepository reservaRepository,
            ReservaHistorialRepository historialRepository,
            ClienteRepository clienteRepository,
            OportunidadService oportunidadService,
            PendientesCrmService pendientesCrm,
            SimulacionRepository simulacionRepository,
            LoteRepository loteRepository,
            LoteHistorialEstadoRepository loteHistorialRepository,
            CrmAlcanceService crmAlcanceService,
            JdbcTemplate jdbcTemplate
    ) {
        this.reservaRepository = reservaRepository;
        this.historialRepository = historialRepository;
        this.clienteRepository = clienteRepository;
        this.oportunidadService = oportunidadService;
        this.pendientesCrm = pendientesCrm;
        this.simulacionRepository = simulacionRepository;
        this.loteRepository = loteRepository;
        this.loteHistorialRepository = loteHistorialRepository;
        this.crmAlcanceService = crmAlcanceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> listar(
            ContextoUsuario contexto,
            EstadoReserva estado,
            Long proyectoId,
            Long clienteId,
            Long loteId,
            int pagina,
            int tamano
    ) {
        Specification<Reserva> filtro = filtroAlcance(contexto);
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        if (proyectoId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("proyectoId"), proyectoId));
        }
        if (clienteId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("clienteId"), clienteId));
        }
        if (loteId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("loteId"), loteId));
        }
        Page<Reserva> resultado = reservaRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "fechaReserva")
                )
        );
        return PaginaResponse.from(
                resultado,
                reserva -> ReservaResponse.from(reserva, List.of())
        );
    }

    @Transactional(readOnly = true)
    public ReservaResponse obtener(ContextoUsuario contexto, Long reservaId) {
        return respuestaCompleta(obtenerAutorizada(contexto, reservaId));
    }

    @Transactional
    public ReservaResponse crear(
            ContextoUsuario contexto,
            ReservaCrearRequest request
    ) {
        LocalDateTime ahora = ahora();
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                request.oportunidadId()
        );
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
        Cliente cliente = clienteRepository.findByIdAndEmpresaIdAndEstado(
                request.clienteId(),
                contexto.empresaId(),
                "ACTIVO"
        ).orElseThrow(() -> new ReglaNegocioException(
                "El cliente debe estar activo en la empresa"
        ));
        if (!oportunidad.tieneLote(request.loteId())) {
            throw new ReglaNegocioException(
                    "El lote debe estar vinculado a la oportunidad"
            );
        }

        Lote lote = loteRepository.findForUpdate(
                request.loteId(),
                contexto.empresaId(),
                oportunidad.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote no pertenece al proyecto de la oportunidad"
        ));
        if (lote.getEstado() != EstadoLote.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "El lote ya no está disponible"
            );
        }
        if (!request.fechaVencimiento().isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "El vencimiento debe estar en el futuro"
            );
        }
        BigDecimal valorReserva = dinero(request.valorReserva());
        if (valorReserva.compareTo(lote.getValorLista()) > 0) {
            throw new ReglaNegocioException(
                    "El valor de reserva no puede superar el valor del lote"
            );
        }

        Simulacion simulacion = obtenerSimulacion(
                contexto,
                oportunidad,
                lote,
                request.simulacionId(),
                ahora
        );
        oportunidad.validarReserva(cliente.getId());

        Reserva reserva = reservaRepository.save(new Reserva(
                contexto.empresaId(),
                oportunidad.getProyectoId(),
                generarCodigo(),
                oportunidad.getId(),
                simulacion == null ? null : simulacion.getId(),
                cliente.getId(),
                lote.getId(),
                oportunidad.getAsesorId(),
                ahora,
                request.fechaVencimiento(),
                valorReserva,
                request.formaPago().trim()
        ));
        // El cliente se vincula a la oportunidad (MongoDB) cuando esta transacción se confirma.
        pendientesCrm.clienteDeReserva(
                contexto.empresaId(),
                oportunidad.getId(),
                cliente.getId(),
                contexto.usuarioId()
        );
        if (simulacion != null) {
            pendientesCrm.simulacionAceptada(
                    contexto.empresaId(),
                    oportunidad.getId(),
                    simulacion.getId(),
                    contexto.usuarioId()
            );
        }

        EstadoLote estadoAnterior = lote.reservar();
        loteHistorialRepository.save(new LoteHistorialEstado(
                lote.getId(),
                estadoAnterior,
                lote.getEstado(),
                "RESERVA",
                reserva.getId(),
                "Lote bloqueado por reserva " + reserva.getCodigo(),
                contexto.usuarioId()
        ));
        guardarHistorial(
                reserva,
                null,
                "Reserva creada",
                contexto.usuarioId()
        );
        return respuestaCompleta(reserva);
    }

    @Transactional
    public ReservaResponse extender(
            ContextoUsuario contexto,
            Long reservaId,
            LocalDateTime nuevaFecha
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        reserva.extender(nuevaFecha, ahora());
        guardarHistorial(
                reserva,
                reserva.getEstado(),
                "Vencimiento extendido hasta " + nuevaFecha,
                contexto.usuarioId()
        );
        return respuestaCompleta(reserva);
    }

    @Transactional
    public ReservaResponse confirmarPago(
            ContextoUsuario contexto,
            Long reservaId,
            Long comprobanteArchivoId
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        validarArchivo(
                comprobanteArchivoId,
                contexto.empresaId(),
                reserva.getProyectoId()
        );
        EstadoReserva anterior = reserva.confirmarPago(
                ahora(),
                contexto.usuarioId(),
                comprobanteArchivoId
        );
        guardarHistorial(
                reserva,
                anterior,
                "Pago de reserva confirmado",
                contexto.usuarioId()
        );
        return respuestaCompleta(reserva);
    }

    @Transactional
    public ReservaResponse rechazarPago(
            ContextoUsuario contexto,
            Long reservaId,
            String motivo
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        EstadoReserva anterior = reserva.rechazarPago(motivo);
        liberarLote(reserva, contexto.usuarioId(), "Pago rechazado");
        guardarHistorial(reserva, anterior, motivo, contexto.usuarioId());
        return respuestaCompleta(reserva);
    }

    @Transactional
    public ReservaResponse reversarPago(
            ContextoUsuario contexto,
            Long reservaId,
            String motivo
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        EstadoReserva anterior = reserva.reversarPagoConfirmado();
        guardarHistorial(
                reserva,
                anterior,
                "Pago de reserva reversado: " + motivo,
                contexto.usuarioId()
        );
        EstadoReserva antesVencimiento = reserva.vencer(ahora());
        if (antesVencimiento != null) {
            liberarLote(
                    reserva,
                    contexto.usuarioId(),
                    "Reserva vencida al reversar el pago"
            );
            guardarHistorial(
                    reserva,
                    antesVencimiento,
                    "Reserva vencida al reversar el pago",
                    contexto.usuarioId()
            );
        }
        return respuestaCompleta(reserva);
    }

    @Transactional
    public ReservaResponse cancelar(
            ContextoUsuario contexto,
            Long reservaId,
            String motivo
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        EstadoReserva anterior = reserva.cancelar(motivo);
        liberarLote(reserva, contexto.usuarioId(), "Reserva cancelada");
        guardarHistorial(reserva, anterior, motivo, contexto.usuarioId());
        return respuestaCompleta(reserva);
    }

    @Transactional
    public Reserva convertirParaContrato(
            ContextoUsuario contexto,
            Long reservaId,
            LocalDateTime fechaConversion
    ) {
        Reserva reserva = obtenerParaActualizar(contexto, reservaId);
        EstadoReserva anterior = reserva.convertir(fechaConversion);
        guardarHistorial(
                reserva,
                anterior,
                "Reserva convertida en contrato",
                contexto.usuarioId()
        );
        return reserva;
    }

    @Transactional
    public int procesarVencimientos() {
        LocalDateTime fecha = ahora();
        List<Reserva> vencidas = reservaRepository.findVencidasForUpdate(
                ESTADOS_VIGENTES,
                fecha,
                PageRequest.of(0, 100)
        );
        for (Reserva reserva : vencidas) {
            EstadoReserva anterior = reserva.vencer(fecha);
            if (anterior == null) {
                continue;
            }
            liberarLote(reserva, null, "Reserva vencida automáticamente");
            guardarHistorial(
                    reserva,
                    anterior,
                    "Vencimiento automático",
                    null
            );
        }
        return vencidas.size();
    }

    @Transactional(readOnly = true)
    public Reserva obtenerAutorizada(
            ContextoUsuario contexto,
            Long reservaId
    ) {
        Reserva reserva = reservaRepository
                .findByIdAndEmpresaId(reservaId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La reserva no existe"
                ));
        verificarAlcance(contexto, reserva);
        return reserva;
    }

    @Transactional
    public Reserva obtenerParaActualizar(
            ContextoUsuario contexto,
            Long reservaId
    ) {
        Reserva reserva = reservaRepository
                .findForUpdate(reservaId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La reserva no existe"
                ));
        verificarAlcance(contexto, reserva);
        return reserva;
    }

    private Simulacion obtenerSimulacion(
            ContextoUsuario contexto,
            Oportunidad oportunidad,
            Lote lote,
            Long simulacionId,
            LocalDateTime fecha
    ) {
        if (simulacionId == null) {
            return null;
        }
        Simulacion simulacion = simulacionRepository.findByIdAndEmpresaId(
                simulacionId,
                contexto.empresaId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "La simulación no existe en la empresa"
        ));
        if (!oportunidad.getId().equals(simulacion.getOportunidadId())
                || !lote.getId().equals(simulacion.getLoteId())
                || !oportunidad.getProyectoId().equals(
                        simulacion.getProyectoId()
                )) {
            throw new ReglaNegocioException(
                    "La simulación no corresponde a la oportunidad y al lote"
            );
        }
        // La simulación vive en MongoDB: aquí solo se valida. Se marca ACEPTADA desde la cola
        // crm_pendientes cuando esta transacción se confirma. El bloqueo del lote serializa las
        // reservas que podrían usar la misma simulación.
        simulacion.validarAceptacion(fecha);
        return simulacion;
    }

    private void liberarLote(
            Reserva reserva,
            Long usuarioId,
            String motivo
    ) {
        Lote lote = loteRepository.findForUpdate(
                reserva.getLoteId(),
                reserva.getEmpresaId(),
                reserva.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote de la reserva no existe"
        ));
        EstadoLote anterior = lote.liberarReserva();
        loteHistorialRepository.save(new LoteHistorialEstado(
                lote.getId(),
                anterior,
                lote.getEstado(),
                "RESERVA",
                reserva.getId(),
                motivo,
                usuarioId
        ));
    }

    private void guardarHistorial(
            Reserva reserva,
            EstadoReserva anterior,
            String motivo,
            Long usuarioId
    ) {
        historialRepository.save(new ReservaHistorial(
                reserva.getId(),
                anterior,
                reserva.getEstado(),
                motivo,
                usuarioId
        ));
    }

    private void verificarAlcance(
            ContextoUsuario contexto,
            Reserva reserva
    ) {
        crmAlcanceService.verificarRegistro(
                contexto,
                reserva.getProyectoId(),
                reserva.getAsesorId()
        );
    }

    private Specification<Reserva> filtroAlcance(
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
            Predicate proyecto = alcance.proyectos().isEmpty()
                    ? cb.disjunction()
                    : root.get("proyectoId").in(alcance.proyectos());
            Predicate propios = alcance.registrosPropios()
                    ? cb.equal(root.get("asesorId"), contexto.usuarioId())
                    : cb.disjunction();
            return cb.and(empresa, cb.or(proyecto, propios));
        };
    }

    private ReservaResponse respuestaCompleta(Reserva reserva) {
        List<ReservaHistorialResponse> historial = historialRepository
                .findByReservaIdOrderByCreadoEnAsc(reserva.getId())
                .stream()
                .map(ReservaHistorialResponse::from)
                .toList();
        return ReservaResponse.from(reserva, historial);
    }

    private void validarArchivo(
            Long archivoId,
            Long empresaId,
            Long proyectoId
    ) {
        if (archivoId == null) {
            return;
        }
        Long cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM archivos
                WHERE id = ?
                  AND empresa_id = ?
                  AND (proyecto_id IS NULL OR proyecto_id = ?)
                  AND estado = 'ACTIVO'
                """, Long.class, archivoId, empresaId, proyectoId);
        if (cantidad == null || cantidad == 0) {
            throw new ReglaNegocioException(
                    "El comprobante no pertenece a la empresa o al proyecto"
            );
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String generarCodigo() {
        return "RSV-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
