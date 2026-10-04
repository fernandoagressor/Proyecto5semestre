package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DocumentoCarteraRequest(@NotNull Long contratoId, @NotNull LocalDate fecha, @NotNull Long archivoId) {
}
