package com.inmobiliaria.inmobiliaria_api.service.ventas.flujo;

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

    public FlujoClienteLoteService(
            ClienteService clienteService,
            ProspectoService prospectoService,
            OportunidadService oportunidadService,
            ReservaService reservaService
    ) {
        this.clienteService = clienteService;
        this.prospectoService = prospectoService;
        this.oportunidadService = oportunidadService;
        this.reservaService = reservaService;
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
        ClienteRequest datosCliente = request.cliente();
        if (esVacio(datosCliente.telefono()) && esVacio(datosCliente.correo())) {
            throw new ReglaNegocioException(
                    "Para vincular un lote, el cliente debe tener teléfono o correo"
            );
        }

        ClienteResponse cliente = clienteService.crear(contexto, datosCliente);
        ProspectoResponse prospecto = prospectoService.crear(
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
        prospecto = prospectoService.calificar(
                contexto,
                prospecto.id(),
                new ProspectoCalificacionRequest(
                        EstadoProspecto.CALIFICADO,
                        TemperaturaProspecto.CALIENTE,
                        "Cliente listo para vinculación y reserva"
                )
        );

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
