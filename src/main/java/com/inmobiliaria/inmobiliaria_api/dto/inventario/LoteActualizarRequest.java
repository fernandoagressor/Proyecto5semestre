package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Editar un lote: identificación, medidas, ubicación y, opcionalmente, el precio. Si el precio
 * cambia queda en el historial de precios. Un lote PENDIENTE pasa a DISPONIBLE cuando queda con
 * área y precio.
 */
public record LoteActualizarRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 30) String numero,
        @DecimalMin(value = "0.01", message = "El área debe ser mayor que cero") @Digits(integer = 10, fraction = 2)
        BigDecimal areaM2,
        @DecimalMin(value = "0.01", message = "El frente debe ser mayor que cero") @Digits(integer = 8, fraction = 2)
        BigDecimal frenteM,
        @DecimalMin(value = "0.01", message = "El fondo debe ser mayor que cero") @Digits(integer = 8, fraction = 2)
        BigDecimal fondoM,
        @Size(max = 300) String ubicacionReferencia,
        @Size(max = 10000) String descripcion,
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo") @Digits(integer = 17, fraction = 2)
        BigDecimal valorLista,
        @Size(max = 300) String motivoPrecio
) {
}
