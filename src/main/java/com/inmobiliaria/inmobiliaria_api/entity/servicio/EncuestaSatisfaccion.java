package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;

/**
 * Calificación que el cliente da a la atención de una PQRS; vive embebida en
 * {@link SolicitudPqrs} (una por solicitud).
 */
public class EncuestaSatisfaccion {
    private Long clienteId;
    private int puntuacion;
    private String comentario;
    private LocalDateTime respondidaEn;

    protected EncuestaSatisfaccion() { }

    public EncuestaSatisfaccion(Long clienteId, int puntuacion, String comentario, LocalDateTime respondidaEn) {
        if (puntuacion < 1 || puntuacion > 5) {
            throw new ReglaNegocioException("La puntuación debe estar entre 1 y 5");
        }
        this.clienteId = clienteId;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
        this.respondidaEn = respondidaEn;
    }

    public Long getClienteId() { return clienteId; }
    public int getPuntuacion() { return puntuacion; }
    public String getComentario() { return comentario; }
    public LocalDateTime getRespondidaEn() { return respondidaEn; }
}
