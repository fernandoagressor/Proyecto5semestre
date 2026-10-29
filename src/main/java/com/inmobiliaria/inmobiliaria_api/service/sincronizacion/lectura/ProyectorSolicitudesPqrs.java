package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code solicitudes_pqrs} → {@code lectura_solicitudes_pqrs}, y su encuesta embebida →
 * {@code lectura_encuestas_satisfaccion} (una fila por solicitud).
 */
@Component
public class ProyectorSolicitudesPqrs extends ProyectorTablaEspejo {

    static final String ENCUESTAS = "lectura_encuestas_satisfaccion";

    public ProyectorSolicitudesPqrs(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "solicitudes_pqrs", "lectura_solicitudes_pqrs");
    }

    @Override
    Map<String, Object> columnas(LectorDocumento d) {
        return fila(
                "empresa_id", d.largoObligatorio("empresaId"),
                "proyecto_id", d.largo("proyectoId"),
                "tipo", d.textoObligatorio("tipo"),
                "estado", d.textoObligatorio("estado"),
                "fecha_limite", d.fecha("fechaLimite"),
                "fecha_primera_respuesta", d.fecha("fechaPrimeraRespuesta"),
                "creado_en", d.fechaObligatoria("creadoEn"));
    }

    @Override
    void proyectarDependientes(Long id, LectorDocumento d, boolean eliminado) {
        LectorDocumento encuesta = eliminado ? null : d.subdocumento("encuesta");
        if (encuesta == null || encuesta.largo("puntuacion") == null) {
            borrarFila(ENCUESTAS, "solicitud_id", id);
            return;
        }
        guardarFila(ENCUESTAS, "solicitud_id", id, fila(
                "empresa_id", d.largoObligatorio("empresaId"),
                "proyecto_id", d.largo("proyectoId"),
                "puntuacion", encuesta.largo("puntuacion"),
                "respondida_en", encuesta.fechaObligatoria("respondidaEn")));
    }
}
