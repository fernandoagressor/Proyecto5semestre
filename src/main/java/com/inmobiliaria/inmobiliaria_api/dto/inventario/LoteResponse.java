package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoteResponse(
        Long id,
        Long proyectoId,
        Long etapaId,
        Long manzanaId,
        String codigo,
        String numero,
        BigDecimal areaM2,
        BigDecimal frenteM,
        BigDecimal fondoM,
        BigDecimal valorLista,
        EstadoLote estado,
        boolean disponible,
        String ubicacionReferencia,
        String descripcion,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static LoteResponse from(Lote lote) {
        return new LoteResponse(
                lote.getId(),
                lote.getProyectoId(),
                lote.getEtapaId(),
                lote.getManzanaId(),
                lote.getCodigo(),
                lote.getNumero(),
                lote.getAreaM2(),
                lote.getFrenteM(),
                lote.getFondoM(),
                lote.getValorLista(),
                lote.getEstado(),
                lote.isDisponible(),
                lote.getUbicacionReferencia(),
                lote.getDescripcion(),
                lote.getVersion(),
                lote.getCreadoEn(),
                lote.getActualizadoEn()
        );
    }
}
