package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.NotNull;

public record ConciliarMovimientoRequest(@NotNull Long pagoId) {
}
