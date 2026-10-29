package com.inmobiliaria.inmobiliaria_api.service.ventas.contrato;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoCondicionesRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoFirmanteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoFirmanteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoVersionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.FirmaContratoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.FirmaContratoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.LoteHistorialEstado;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoCliente;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoVersion;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoContrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoValidacionFirma;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.FirmaContrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.MetodoFirma;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.RolContratoCliente;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteHistorialEstadoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.ContratoClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.ContratoFirmanteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.ContratoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.ContratoVersionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.FirmaContratoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.PlanPagoService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.crm.PendientesCrmService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
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
public class ContratoService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final ContratoRepository contratoRepository;
    private final ContratoClienteRepository contratoClienteRepository;
    private final ContratoVersionRepository versionRepository;
    private final ContratoFirmanteRepository firmanteRepository;
    private final FirmaContratoRepository firmaRepository;
    private final ReservaService reservaService;
    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;
    private final SimulacionRepository simulacionRepository;
    private final LoteRepository loteRepository;
    private final LoteHistorialEstadoRepository loteHistorialRepository;
    private final OportunidadService oportunidadService;
    private final PendientesCrmService pendientesCrm;
    private final CrmAlcanceService crmAlcanceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlanPagoService planPagoService;

    public ContratoService(
            ContratoRepository contratoRepository,
            ContratoClienteRepository contratoClienteRepository,
            ContratoVersionRepository versionRepository,
            ContratoFirmanteRepository firmanteRepository,
            FirmaContratoRepository firmaRepository,
            ReservaService reservaService,
            ClienteRepository clienteRepository,
            PersonaRepository personaRepository,
            SimulacionRepository simulacionRepository,
            LoteRepository loteRepository,
            LoteHistorialEstadoRepository loteHistorialRepository,
            OportunidadService oportunidadService,
            PendientesCrmService pendientesCrm,
            CrmAlcanceService crmAlcanceService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlanPagoService planPagoService
    ) {
        this.contratoRepository = contratoRepository;
        this.contratoClienteRepository = contratoClienteRepository;
        this.versionRepository = versionRepository;
        this.firmanteRepository = firmanteRepository;
        this.firmaRepository = firmaRepository;
        this.reservaService = reservaService;
        this.clienteRepository = clienteRepository;
        this.personaRepository = personaRepository;
        this.simulacionRepository = simulacionRepository;
        this.loteRepository = loteRepository;
        this.loteHistorialRepository = loteHistorialRepository;
        this.oportunidadService = oportunidadService;
        this.pendientesCrm = pendientesCrm;
        this.crmAlcanceService = crmAlcanceService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.planPagoService = planPagoService;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ContratoResponse> listar(
            ContextoUsuario contexto,
            EstadoContrato estado,
            Long proyectoId,
            Long clienteId,
            Long loteId,
            int pagina,
            int tamano
    ) {
        Specification<Contrato> filtro = filtroAlcance(contexto);
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
                    cb.equal(root.get("clientePrincipalId"), clienteId));
        }
        if (loteId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("loteId"), loteId));
        }
        Page<Contrato> resultado = contratoRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn")
                )
        );
        return PaginaResponse.from(
                resultado,
                contrato -> ContratoResponse.from(
                        contrato,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    @Transactional(readOnly = true)
    public ContratoResponse obtener(
            ContextoUsuario contexto,
            Long contratoId
    ) {
        return respuestaCompleta(obtenerAutorizado(contexto, contratoId));
    }

    @Transactional
    public ContratoResponse convertirReserva(
            ContextoUsuario contexto,
            Long reservaId,
            ContratoCondicionesRequest request
    ) {
        LocalDateTime fecha = ahora();
        Reserva reserva = reservaService.convertirParaContrato(
                contexto,
                reservaId,
                fecha
        );
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                reserva.getOportunidadId()
        );
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA
                || !reserva.getProyectoId().equals(
                        oportunidad.getProyectoId()
                )) {
            throw new ReglaNegocioException(
                    "La oportunidad de la reserva no está abierta"
            );
        }
        Cliente cliente = clienteActivo(
                reserva.getClienteId(),
                contexto.empresaId()
        );
        Lote lote = loteRepository.findForUpdate(
                reserva.getLoteId(),
                contexto.empresaId(),
                reserva.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote de la reserva no existe"
        ));
        if (lote.getEstado() != EstadoLote.RESERVADO) {
            throw new ReglaNegocioException(
                    "El lote debe permanecer reservado"
            );
        }

        BigDecimal valorBase = valorBaseContrato(reserva, lote);
        validarDescuentoSimulado(reserva, request.descuento());
        Contrato contrato = contratoRepository.save(new Contrato(
                contexto.empresaId(),
                reserva.getProyectoId(),
                generarCodigo(),
                oportunidad.getId(),
                reserva.getId(),
                cliente.getId(),
                lote.getId(),
                oportunidad.getAsesorId(),
                request.tipoContrato(),
                valorBase,
                dinero(request.otrosConceptos()),
                dinero(request.descuento()),
                dinero(request.cuotaInicial()),
                request.numeroCuotas(),
                request.fechaInicio(),
                request.fechaFinalizacion()
        ));
        contratoClienteRepository.save(new ContratoCliente(
                contrato.getId(),
                cliente.getId(),
                RolContratoCliente.PRINCIPAL,
                BigDecimal.valueOf(100).setScale(2)
        ));
        firmanteRepository.save(new ContratoFirmante(
                contrato.getId(),
                cliente.getPersonaId(),
                TipoFirmante.COMPRADOR,
                1,
                null
        ));
        registrarVersion(
                contrato,
                null,
                "Versión inicial creada desde la reserva",
                contexto.usuarioId()
        );
        return respuestaCompleta(contrato);
    }

    @Transactional
    public ContratoResponse actualizar(
            ContextoUsuario contexto,
            Long contratoId,
            ContratoCondicionesRequest request
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        validarDescuentoSimuladoPorContrato(
                contexto,
                contrato,
                request.descuento()
        );
        contrato.actualizarCondiciones(
                request.tipoContrato(),
                contrato.getValorLote(),
                dinero(request.otrosConceptos()),
                dinero(request.descuento()),
                dinero(request.cuotaInicial()),
                request.numeroCuotas(),
                request.fechaInicio(),
                request.fechaFinalizacion()
        );
        registrarVersion(
                contrato,
                null,
                "Condiciones contractuales actualizadas",
                contexto.usuarioId()
        );
        return respuestaCompleta(contrato);
    }

    @Transactional
    public ContratoClienteResponse agregarCliente(
            ContextoUsuario contexto,
            Long contratoId,
            ContratoClienteRequest request
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        exigirEditable(contrato);
        if (request.rol() == RolContratoCliente.PRINCIPAL) {
            throw new ReglaNegocioException(
                    "El contrato ya tiene un cliente principal"
            );
        }
        Cliente cliente = clienteActivo(
                request.clienteId(),
                contexto.empresaId()
        );
        if (contratoClienteRepository
                .existsByContratoIdAndClienteIdAndRolEnContrato(
                        contratoId,
                        cliente.getId(),
                        request.rol()
                )) {
            throw new ConflictoException(
                    "El cliente ya tiene ese rol en el contrato"
            );
        }
        BigDecimal participacion = dinero(request.porcentajeParticipacion());
        if (request.rol() == RolContratoCliente.COPROPIETARIO) {
            if (participacion.signum() <= 0) {
                throw new ReglaNegocioException(
                        "El copropietario debe tener participación"
                );
            }
            ContratoCliente principal = contratoClienteRepository
                    .findByContratoIdAndRolEnContrato(
                            contratoId,
                            RolContratoCliente.PRINCIPAL
                    ).orElseThrow(() -> new ReglaNegocioException(
                            "El contrato no tiene cliente principal"
                    ));
            BigDecimal restante = principal
                    .getPorcentajeParticipacion()
                    .subtract(participacion);
            if (restante.signum() <= 0) {
                throw new ReglaNegocioException(
                        "El cliente principal debe conservar participación"
                );
            }
            principal.ajustarParticipacion(restante);
        }
        ContratoCliente relacion = contratoClienteRepository.save(
                new ContratoCliente(
                        contratoId,
                        cliente.getId(),
                        request.rol(),
                        participacion
                )
        );
        registrarVersion(
                contrato,
                null,
                "Participante agregado al contrato",
                contexto.usuarioId()
        );
        return ContratoClienteResponse.from(relacion);
    }

    @Transactional
    public ContratoFirmanteResponse agregarFirmante(
            ContextoUsuario contexto,
            Long contratoId,
            ContratoFirmanteRequest request
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        exigirEditable(contrato);
        Persona persona = personaRepository.findById(request.personaId())
                .orElseThrow(() -> new ReglaNegocioException(
                        "La persona firmante no existe"
                ));
        validarPersonaEmpresa(
                persona.getId(),
                contexto.empresaId()
        );
        ContratoFirmante firmante = firmanteRepository.save(
                new ContratoFirmante(
                        contratoId,
                        persona.getId(),
                        request.tipoFirmante(),
                        request.ordenFirma(),
                        request.fechaProgramada()
                )
        );
        return ContratoFirmanteResponse.from(firmante, List.of());
    }

    @Transactional
    public ContratoVersionResponse adjuntarDocumento(
            ContextoUsuario contexto,
            Long contratoId,
            Long archivoId,
            String motivo
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        exigirEditable(contrato);
        validarArchivo(
                archivoId,
                contexto.empresaId(),
                contrato.getProyectoId()
        );
        return ContratoVersionResponse.from(registrarVersion(
                contrato,
                archivoId,
                motivo,
                contexto.usuarioId()
        ));
    }

    @Transactional
    public ContratoResponse iniciarNegociacion(
            ContextoUsuario contexto,
            Long contratoId
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        contrato.iniciarNegociacion();
        registrarVersion(
                contrato,
                null,
                "Contrato enviado a negociación",
                contexto.usuarioId()
        );
        return respuestaCompleta(contrato);
    }

    @Transactional
    public ContratoResponse enviarAFirma(
            ContextoUsuario contexto,
            Long contratoId
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        List<ContratoFirmante> firmantes = firmanteRepository
                .findByContratoIdOrderByOrdenFirmaAsc(contratoId);
        if (firmantes.isEmpty()) {
            throw new ReglaNegocioException(
                    "El contrato debe tener al menos un firmante"
            );
        }
        boolean documentoVigente = versionRepository
                .findByContratoIdAndVigenteTrue(contratoId)
                .stream()
                .anyMatch(version -> version.getArchivoId() != null);
        if (!documentoVigente) {
            throw new ReglaNegocioException(
                    "Debe adjuntar la versión documental que será firmada"
            );
        }
        contrato.enviarAFirma();
        LocalDateTime fecha = ahora();
        firmantes.stream()
                .filter(firmante ->
                        firmante.getEstado() == EstadoFirmante.PENDIENTE)
                .forEach(firmante -> firmante.marcarEnviada(fecha));
        return respuestaCompleta(contrato);
    }

    @Transactional
    public ContratoResponse registrarFirma(
            ContextoUsuario contexto,
            Long contratoId,
            Long firmanteId,
            FirmaContratoRequest request
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        if (contrato.getEstado() != EstadoContrato.PENDIENTE_FIRMA) {
            throw new ReglaNegocioException(
                    "El contrato no está pendiente de firma"
            );
        }
        ContratoFirmante firmante = firmanteRepository.findForUpdate(
                firmanteId,
                contratoId
        ).orElseThrow(() -> new RecursoNoEncontradoException(
                "El firmante no existe en el contrato"
        ));
        validarEvidenciaFirma(request);
        validarArchivo(
                request.archivoFirmaId(),
                contexto.empresaId(),
                contrato.getProyectoId()
        );
        LocalDateTime fechaFirma = request.firmadaEn() == null
                ? ahora()
                : request.firmadaEn();
        if (fechaFirma.isAfter(ahora())) {
            throw new ReglaNegocioException(
                    "La firma no puede registrarse con fecha futura"
            );
        }
        firmaRepository.save(new FirmaContrato(
                firmante.getId(),
                request.metodo(),
                normalizar(request.proveedor()),
                normalizar(request.referenciaExterna()),
                request.hashDocumento().toLowerCase(Locale.ROOT),
                normalizar(request.ipOrigen()),
                fechaFirma,
                request.estadoValidacion()
        ));
        firmante.registrarFirma(
                fechaFirma,
                request.archivoFirmaId(),
                request.estadoValidacion()
        );
        if (request.estadoValidacion() == EstadoValidacionFirma.VALIDA
                && todosFirmados(contratoId)) {
            activarVenta(contexto, contrato, fechaFirma);
        }
        return respuestaCompleta(contrato);
    }

    @Transactional
    public ContratoResponse anular(
            ContextoUsuario contexto,
            Long contratoId,
            String motivo
    ) {
        Contrato contrato = obtenerParaActualizar(contexto, contratoId);
        EstadoContrato anterior = contrato.anular(
                motivo,
                ahora(),
                contexto.usuarioId()
        );
        Lote lote = loteRepository.findForUpdate(
                contrato.getLoteId(),
                contrato.getEmpresaId(),
                contrato.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote del contrato no existe"
        ));
        EstadoLote estadoAnterior;
        if (anterior == EstadoContrato.VIGENTE) {
            estadoAnterior = lote.revertirVentaAnulada();
            reabrirVenta(contexto, contrato);
        } else {
            estadoAnterior = lote.liberarReserva();
        }
        loteHistorialRepository.save(new LoteHistorialEstado(
                lote.getId(),
                estadoAnterior,
                lote.getEstado(),
                "CONTRATO",
                contrato.getId(),
                "Contrato anulado: " + motivo.trim(),
                contexto.usuarioId()
        ));
        registrarVersion(
                contrato,
                null,
                "Contrato anulado: " + motivo.trim(),
                contexto.usuarioId()
        );
        return respuestaCompleta(contrato);
    }

    @Transactional(readOnly = true)
    public Contrato obtenerAutorizado(
            ContextoUsuario contexto,
            Long contratoId
    ) {
        Contrato contrato = contratoRepository
                .findByIdAndEmpresaId(contratoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El contrato no existe"
                ));
        verificarAlcance(contexto, contrato);
        return contrato;
    }

    @Transactional
    public Contrato obtenerParaActualizar(
            ContextoUsuario contexto,
            Long contratoId
    ) {
        Contrato contrato = contratoRepository
                .findForUpdate(contratoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El contrato no existe"
                ));
        verificarAlcance(contexto, contrato);
        return contrato;
    }

    private void activarVenta(
            ContextoUsuario contexto,
            Contrato contrato,
            LocalDateTime fechaFirma
    ) {
        contrato.activar(fechaFirma.toLocalDate());
        planPagoService.generarAutomatico(contexto, contrato);
        Lote lote = loteRepository.findForUpdate(
                contrato.getLoteId(),
                contrato.getEmpresaId(),
                contrato.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote del contrato no existe"
        ));
        EstadoLote anterior = lote.vender();
        loteHistorialRepository.save(new LoteHistorialEstado(
                lote.getId(),
                anterior,
                lote.getEstado(),
                "CONTRATO",
                contrato.getId(),
                "Venta activada por firmas completas",
                contexto.usuarioId()
        ));
        // La oportunidad (GANADA) y el prospecto (CONVERTIDO) viven en MongoDB: el cambio se
        // encola en esta misma transacción y se aplica al confirmarse (rama 8).
        pendientesCrm.ventaGanada(
                contexto.empresaId(),
                contrato.getOportunidadId(),
                contrato.getId(),
                contrato.getClientePrincipalId(),
                fechaFirma,
                contexto.usuarioId()
        );
        registrarVersion(
                contrato,
                null,
                "Contrato activado por firmas completas",
                contexto.usuarioId()
        );
    }

    private void reabrirVenta(
            ContextoUsuario contexto,
            Contrato contrato
    ) {
        pendientesCrm.ventaAnulada(
                contexto.empresaId(),
                contrato.getOportunidadId(),
                contrato.getId(),
                contexto.usuarioId()
        );
    }

    private boolean todosFirmados(Long contratoId) {
        List<ContratoFirmante> firmantes = firmanteRepository
                .findByContratoIdOrderByOrdenFirmaAsc(contratoId);
        return !firmantes.isEmpty()
                && firmantes.stream().allMatch(firmante ->
                firmante.getEstado() == EstadoFirmante.FIRMADA);
    }

    private ContratoVersion registrarVersion(
            Contrato contrato,
            Long archivoId,
            String motivo,
            Long usuarioId
    ) {
        versionRepository.findByContratoIdAndVigenteTrue(contrato.getId())
                .forEach(ContratoVersion::desactivar);
        versionRepository.flush();
        int numero = versionRepository.maxNumeroVersion(contrato.getId()) + 1;
        return versionRepository.save(new ContratoVersion(
                contrato.getId(),
                numero,
                archivoId,
                snapshot(contrato),
                requerido(motivo),
                usuarioId
        ));
    }

    private ObjectNode snapshot(Contrato contrato) {
        ObjectNode datos = objectMapper.createObjectNode();
        datos.put("codigo", contrato.getCodigo());
        datos.put("tipoContrato", contrato.getTipoContrato().name());
        datos.put("valorLote", contrato.getValorLote());
        datos.put("otrosConceptos", contrato.getOtrosConceptos());
        datos.put("descuento", contrato.getDescuento());
        datos.put("valorTotal", contrato.getValorTotal());
        datos.put("cuotaInicial", contrato.getCuotaInicial());
        datos.put("saldoFinanciado", contrato.getSaldoFinanciado());
        datos.put("numeroCuotas", contrato.getNumeroCuotas());
        datos.put("fechaInicio", contrato.getFechaInicio().toString());
        if (contrato.getFechaFinalizacion() == null) {
            datos.putNull("fechaFinalizacion");
        } else {
            datos.put(
                    "fechaFinalizacion",
                    contrato.getFechaFinalizacion().toString()
            );
        }
        datos.put("estado", contrato.getEstado().name());
        return datos;
    }

    private BigDecimal valorBaseContrato(Reserva reserva, Lote lote) {
        if (reserva.getSimulacionId() == null) {
            return dinero(lote.getValorLista());
        }
        return simulacionRepository.findByIdAndEmpresaId(
                reserva.getSimulacionId(),
                reserva.getEmpresaId()
        ).map(Simulacion::getValorLote).map(ContratoService::dinero)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La simulación aceptada ya no existe"
                ));
    }

    private void validarDescuentoSimulado(
            Reserva reserva,
            BigDecimal descuento
    ) {
        if (reserva.getSimulacionId() == null) {
            if (dinero(descuento).signum() > 0) {
                throw new ReglaNegocioException(
                        "Los descuentos requieren una simulación aceptada"
                );
            }
            return;
        }
        Simulacion simulacion = simulacionRepository.findByIdAndEmpresaId(
                reserva.getSimulacionId(),
                reserva.getEmpresaId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "La simulación aceptada ya no existe"
        ));
        if (dinero(descuento).compareTo(simulacion.getDescuento()) > 0) {
            throw new ReglaNegocioException(
                    "El descuento supera el aprobado en la simulación"
            );
        }
    }

    private void validarDescuentoSimuladoPorContrato(
            ContextoUsuario contexto,
            Contrato contrato,
            BigDecimal descuento
    ) {
        if (contrato.getReservaId() == null) {
            return;
        }
        Reserva reserva = reservaService.obtenerAutorizada(
                contexto,
                contrato.getReservaId()
        );
        validarDescuentoSimulado(reserva, descuento);
    }

    private Cliente clienteActivo(Long clienteId, Long empresaId) {
        return clienteRepository.findByIdAndEmpresaIdAndEstado(
                clienteId,
                empresaId,
                "ACTIVO"
        ).orElseThrow(() -> new ReglaNegocioException(
                "El cliente debe estar activo en la empresa"
        ));
    }

    private void validarPersonaEmpresa(Long personaId, Long empresaId) {
        Long cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM personas p
                WHERE p.id = ?
                  AND p.activo = 1
                  AND (
                        EXISTS (
                          SELECT 1 FROM clientes c
                          WHERE c.persona_id = p.id
                            AND c.empresa_id = ?
                        )
                        OR EXISTS (
                          SELECT 1 FROM usuarios u
                          WHERE u.persona_id = p.id
                            AND u.empresa_id = ?
                        )
                  )
                """, Long.class, personaId, empresaId, empresaId);
        if (cantidad == null || cantidad == 0) {
            throw new ReglaNegocioException(
                    "La persona no pertenece al alcance de la empresa"
            );
        }
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
                    "El archivo no pertenece a la empresa o al proyecto"
            );
        }
    }

    private static void validarEvidenciaFirma(
            FirmaContratoRequest request
    ) {
        if (request.estadoValidacion() == EstadoValidacionFirma.VALIDA
                && request.archivoFirmaId() == null) {
            throw new ReglaNegocioException(
                    "Una firma válida debe conservar su archivo de evidencia"
            );
        }
        if (request.metodo() != MetodoFirma.MANUSCRITA
                && (normalizar(request.proveedor()) == null
                || normalizar(request.referenciaExterna()) == null)) {
            throw new ReglaNegocioException(
                    "La firma electrónica o digital requiere proveedor y referencia externa"
            );
        }
    }

    private void verificarAlcance(
            ContextoUsuario contexto,
            Contrato contrato
    ) {
        crmAlcanceService.verificarRegistro(
                contexto,
                contrato.getProyectoId(),
                contrato.getAsesorId()
        );
    }

    private Specification<Contrato> filtroAlcance(
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

    private ContratoResponse respuestaCompleta(Contrato contrato) {
        List<ContratoClienteResponse> clientes = contratoClienteRepository
                .findByContratoIdOrderByIdAsc(contrato.getId())
                .stream()
                .map(ContratoClienteResponse::from)
                .toList();
        List<ContratoVersionResponse> versiones = versionRepository
                .findByContratoIdOrderByNumeroVersionDesc(contrato.getId())
                .stream()
                .map(ContratoVersionResponse::from)
                .toList();
        List<ContratoFirmanteResponse> firmantes = firmanteRepository
                .findByContratoIdOrderByOrdenFirmaAsc(contrato.getId())
                .stream()
                .map(firmante -> ContratoFirmanteResponse.from(
                        firmante,
                        firmaRepository
                                .findByContratoFirmanteIdOrderByFirmadaEnAsc(
                                        firmante.getId()
                                )
                                .stream()
                                .map(FirmaContratoResponse::from)
                                .toList()
                ))
                .toList();
        return ContratoResponse.from(
                contrato,
                clientes,
                versiones,
                firmantes
        );
    }

    private static void exigirEditable(Contrato contrato) {
        if (contrato.getEstado() != EstadoContrato.BORRADOR
                && contrato.getEstado() != EstadoContrato.NEGOCIACION) {
            throw new ReglaNegocioException(
                    "El contrato ya no admite cambios de participantes"
            );
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String requerido(String valor) {
        String normalizado = normalizar(valor);
        if (normalizado == null) {
            throw new ReglaNegocioException("El motivo es obligatorio");
        }
        return normalizado;
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static String generarCodigo() {
        return "CTR-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
