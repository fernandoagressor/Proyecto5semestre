package com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;

public record FlujoClienteLoteResponse(
        ClienteResponse cliente,
        OportunidadResponse oportunidad,
        ReservaResponse reserva
) {
}
