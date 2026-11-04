package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.AvanceObra;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Entrada y salida del caso de uso "Avances de obra". */
public final class AvanceObraDtos {

    private AvanceObraDtos() {
    }

    public record RegistroRequest(
            @NotNull @PastOrPresent LocalDate fecha,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal porcentaje,
            @NotBlank @Size(max = 1000) String descripcion
    ) {
    }

    public record Response(
            Long id,
            Long proyectoId,
            LocalDate fecha,
            BigDecimal porcentaje,
            String descripcion,
            Long registradoPor,
            LocalDateTime creadoEn
    ) {
        public static Response from(AvanceObra avance) {
            return new Response(
                    avance.getId(),
                    avance.getProyectoId(),
                    avance.getFecha(),
                    avance.getPorcentaje(),
                    avance.getDescripcion(),
                    avance.getRegistradoPor(),
                    avance.getCreadoEn()
            );
        }
    }
}
