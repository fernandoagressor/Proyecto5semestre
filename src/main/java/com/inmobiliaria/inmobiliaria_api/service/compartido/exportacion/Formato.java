package com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.util.Locale;

/** Formatos de descarga con su tipo MIME y extensión. */
public enum Formato {
    CSV("text/csv;charset=UTF-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String mime;
    private final String extension;

    Formato(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() {
        return mime;
    }

    public String extension() {
        return extension;
    }

    public static Formato de(String valor) {
        try {
            return Formato.valueOf(valor == null ? "PDF" : valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("El formato debe ser CSV, XLSX o PDF");
        }
    }
}
