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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlujoClienteLoteService {

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

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
