package com.inmobiliaria.inmobiliaria_api.dto.compartido;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        boolean primera,
        boolean ultima
) {
    public static <S, T> PaginaResponse<T> from(
            Page<S> page,
            Function<S, T> mapper
    ) {
        return new PaginaResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
