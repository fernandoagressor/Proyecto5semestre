package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CierreCarteraRequest(@NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String periodo) {
}
