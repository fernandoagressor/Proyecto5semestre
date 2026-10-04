package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CierreCarteraResponse(Long id, String periodo, BigDecimal saldoTotal, BigDecimal saldoVencido, BigDecimal recaudoPeriodo, BigDecimal indiceMora, LocalDateTime cerradoEn) {
}
