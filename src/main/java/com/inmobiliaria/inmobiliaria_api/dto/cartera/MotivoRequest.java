package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MotivoRequest(@NotBlank @Size(max = 500) String motivo) {
}
