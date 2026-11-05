package com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;

public record FlujoClienteLoteResponse(
        ClienteResponse cliente,
        OportunidadResponse oportunidad,
        ReservaResponse reserva,
        /** Usuario y contraseña temporal del portal, si se crearon en esta reserva (se ven una sola vez). */
        PortalDtos.Acceso accesoPortal,
        /** El cliente ya tenía acceso a su portal antes de esta reserva. */
        boolean portalExistente
) {
}
