package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code citas_servicio} → {@code lectura_citas_servicio}. */
@Component
public class ProyectorCitasServicio extends ProyectorTablaEspejo {

    public ProyectorCitasServicio(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "citas_servicio", "lectura_citas_servicio");
    }

    @Override
    Map<String, Object> columnas(LectorDocumento d) {
        return fila(
                "empresa_id", d.largoObligatorio("empresaId"),
                "proyecto_id", d.largo("proyectoId"),
                "estado", d.textoObligatorio("estado"),
                "fecha_inicio", d.fechaObligatoria("fechaInicio"));
    }
}
