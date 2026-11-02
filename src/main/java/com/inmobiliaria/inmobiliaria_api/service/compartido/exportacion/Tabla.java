package com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion;

import java.util.List;

/**
 * Tabla lista para exportar a CSV, XLSX o PDF. Las celdas pueden ser texto, números
 * ({@link java.math.BigDecimal}, Long, Integer), fechas ({@link java.time.LocalDate} o
 * {@link java.time.LocalDateTime}) o nulas. {@code moneda} marca las columnas que son pesos.
 */
public record Tabla(String titulo, String subtitulo, List<String> columnas, List<List<Object>> filas,
                    List<Boolean> moneda) {

    public Tabla {
        columnas = List.copyOf(columnas);
        filas = filas.stream().map(f -> java.util.Collections.unmodifiableList(new java.util.ArrayList<>(f))).toList();
        moneda = moneda == null ? columnas.stream().map(c -> false).toList() : List.copyOf(moneda);
    }
}
