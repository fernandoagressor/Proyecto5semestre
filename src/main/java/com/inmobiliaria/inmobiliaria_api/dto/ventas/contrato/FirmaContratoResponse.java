package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoValidacionFirma;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.FirmaContrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.MetodoFirma;
import java.time.LocalDateTime;

public record FirmaContratoResponse(
        Long id,
        MetodoFirma metodo,
        String proveedor,
        String referenciaExterna,
        String hashDocumento,
        String ipOrigen,
        LocalDateTime firmadaEn,
        EstadoValidacionFirma estadoValidacion
) {
    public static FirmaContratoResponse from(FirmaContrato firma) {
        return new FirmaContratoResponse(
                firma.getId(),
                firma.getMetodo(),
                firma.getProveedor(),
                firma.getReferenciaExterna(),
                firma.getHashDocumento(),
                firma.getIpOrigen(),
                firma.getFirmadaEn(),
                firma.getEstadoValidacion()
        );
    }
}
