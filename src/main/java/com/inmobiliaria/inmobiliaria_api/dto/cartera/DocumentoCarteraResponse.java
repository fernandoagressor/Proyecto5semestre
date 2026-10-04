package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentoCarteraResponse(Long id, String tipo, String codigo, Long contratoId, LocalDate fecha, BigDecimal saldoCapital, BigDecimal saldoMora, BigDecimal saldoTotal, Long archivoId) {
}
