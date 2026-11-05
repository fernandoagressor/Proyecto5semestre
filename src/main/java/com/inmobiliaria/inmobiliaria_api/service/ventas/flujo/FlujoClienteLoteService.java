package com.inmobiliaria.inmobiliaria_api.service.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoCalificacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.FuenteProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cliente.ClienteService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.crm.ProspectoService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.reserva.ReservaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FlujoClienteLoteService {

    private static final Logger log = LoggerFactory.getLogger(FlujoClienteLoteService.class);

    private final ClienteService clienteService;
    private final ProspectoService prospectoService;
    private final OportunidadService oportunidadService;
    private final ReservaService reservaService;
    private final LoteRepository loteRepository;

    public FlujoClienteLoteService(
            ClienteService clienteService,
            ProspectoService prospectoService,
            OportunidadService oportunidadService,
            ReservaService reservaService,
            LoteRepository loteRepository
    ) {
        this.clienteService = clienteService;
        this.prospectoService = prospectoService;
        this.oportunidadService = oportunidadService;
        this.reservaService = reservaService;
        this.loteRepository = loteRepository;
    }

    /**
     * Ejecuta el alta comercial completa en una sola transacción. El prospecto y la
     * oportunidad siguen existiendo para conservar trazabilidad, pero no forman parte
     * del flujo visible para el usuario.
     *
     * <p>Desde la rama 8 el prospecto y la oportunidad viven en MongoDB, fuera de la transacción de
     * MySQL. Si el flujo falla después de crearlos (por ejemplo, el lote ya no está disponible),
     * se borran al hacerse el rollback para no dejar registros huérfanos.</p>
     */
    @Transactional
    public FlujoClienteLoteResponse crear(
            ContextoUsuario contexto,
            FlujoClienteLoteRequest request
    ) {
        verificarLoteDisponible(contexto, request);
        ClienteResponse cliente = resolverCliente(contexto, request);
        if (esVacio(cliente.telefono()) && esVacio(cliente.correo())) {
            throw new ReglaNegocioException(
                    "Para reservar, el cliente debe tener teléfono o correo"
                            + (request.clienteId() != null ? ": agrégalo en su ficha de Clientes" : "")
            );
        }
        ProspectoResponse prospecto = prospectoReutilizable(contexto, request.proyectoId(), cliente);
        if (prospecto == null) {
            prospecto = prospectoService.crearRegistroTecnico(
                    contexto,
                    new ProspectoRequest(
                            request.proyectoId(),
                            null,
                            cliente.nombreCompleto(),
                            cliente.telefono(),
                            cliente.correo(),
                            FuenteProspecto.OTRO,
                            null,
                            TemperaturaProspecto.CALIENTE,
                            "Registro técnico creado desde el flujo simplificado cliente-lote"
                    )
            );
            Long prospectoId = prospecto.id();
            compensarSiFalla(() -> prospectoService.eliminarPorCompensacion(prospectoId));
        }
        if (prospecto.estado() != EstadoProspecto.CALIFICADO) {
            prospecto = prospectoService.calificar(
                    contexto,
                    prospecto.id(),
                    new ProspectoCalificacionRequest(
                            EstadoProspecto.CALIFICADO,
                            TemperaturaProspecto.CALIENTE,
                            "Cliente listo para vinculación y reserva"
                    )
            );
        }

        OportunidadResponse oportunidad = oportunidadService.crear(
                contexto,
                new OportunidadRequest(
                        request.proyectoId(),
                        prospecto.id(),
                        null,
                        null,
                        null
                )
        );
        Long oportunidadId = oportunidad.id();
        compensarSiFalla(() -> oportunidadService.eliminarPorCompensacion(oportunidadId));
        oportunidadService.agregarLote(
                contexto,
                oportunidad.id(),
                request.loteId(),
                1
        );
        oportunidadService.seleccionarLote(
                contexto,
                oportunidad.id(),
                request.loteId(),
                true
        );

        ReservaResponse reserva = reservaService.crear(
                contexto,
                new ReservaCrearRequest(
                        oportunidad.id(),
                        null,
                        cliente.id(),
                        request.loteId(),
                        request.fechaVencimiento(),
                        request.valorReserva(),
                        request.formaPago()
                )
        );
        return new FlujoClienteLoteResponse(cliente, oportunidad, reserva);
    }

    /**
     * Antes de crear nada, el lote debe estar disponible. La reserva vuelve a comprobarlo con el
     * lote bloqueado (y la base no admite dos reservas vigentes del mismo lote): esto solo da un
     * mensaje claro sin dejar registros a medias.
     */
    private void verificarLoteDisponible(ContextoUsuario contexto, FlujoClienteLoteRequest request) {
        loteRepository.findByIdAndEmpresaIdAndProyectoId(request.loteId(), contexto.empresaId(), request.proyectoId())
                .filter(lote -> lote.getEstado() != EstadoLote.DISPONIBLE)
                .ifPresent(lote -> {
                    throw new ReglaNegocioException(lote.getEstado() == EstadoLote.PENDIENTE
                            ? "El lote " + lote.getCodigo() + " todavía no tiene medidas y precio: complétalo en Inventario"
                            : "El lote " + lote.getCodigo() + " ya no está disponible: alguien lo acaba de reservar o vender."
                                    + " Elige otro lote");
                });
    }

    /**
     * Cliente de la reserva: el elegido ({@code clienteId}), el que ya existe con ese documento o
     * uno nuevo con los datos recibidos.
     */
    private ClienteResponse resolverCliente(ContextoUsuario contexto, FlujoClienteLoteRequest request) {
        if (request.clienteId() != null) {
            ClienteResponse existente = clienteService.obtener(contexto, request.clienteId());
            if (!"ACTIVO".equals(existente.estado())) {
                throw new ReglaNegocioException(
                        "El cliente " + existente.nombreCompleto() + " no está activo; actívalo en Clientes para reservar"
                );
            }
            return existente;
        }
        ClienteRequest datos = request.cliente();
        if (datos == null) {
            throw new ReglaNegocioException("Elige un cliente o registra uno nuevo para la reserva");
        }
        if (esVacio(datos.telefono()) && esVacio(datos.correo())) {
            throw new ReglaNegocioException(
                    "Para reservar, el cliente debe tener teléfono o correo"
            );
        }
        return clienteService.buscarPorDocumento(contexto, datos.tipoDocumento(), datos.numeroDocumento())
                .orElseGet(() -> clienteService.crear(contexto, datos));
    }

    /**
     * Si el CRM ya tiene a esta persona como prospecto activo (mismo teléfono o correo), de este
     * proyecto o sin proyecto, y sin una oportunidad abierta, la reserva se registra sobre él en vez
     * de duplicarlo. Si su prospecto ya está en otra venta abierta (por ejemplo, reservó otro lote),
     * se crea un registro técnico nuevo: cada reserva lleva su propia oportunidad.
     */
    private ProspectoResponse prospectoReutilizable(
            ContextoUsuario contexto,
            Long proyectoId,
            ClienteResponse cliente
    ) {
        return prospectoService.buscarActivosPorContacto(contexto, cliente.telefono(), cliente.correo())
                .stream()
                .filter(p -> p.proyectoId() == null || p.proyectoId().equals(proyectoId))
                .filter(p -> !oportunidadService.tieneAbierta(p.id()))
                .findFirst()
                .orElse(null);
    }

    private static void compensarSiFalla(Runnable compensacion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    compensacion.run();
                } catch (RuntimeException e) {
                    log.error("No se pudo deshacer en MongoDB un registro del flujo cliente-lote", e);
                }
            }
        });
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
