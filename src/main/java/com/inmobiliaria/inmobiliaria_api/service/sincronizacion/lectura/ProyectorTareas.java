package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code tareas} → {@code lectura_tareas}. */
@Component
public class ProyectorTareas extends ProyectorTablaEspejo {

    public ProyectorTareas(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "tareas", "lectura_tareas");
    }

    @Override
    Map<String, Object> columnas(LectorDocumento d) {
        return fila(
                "empresa_id", d.largoObligatorio("empresaId"),
                "proyecto_id", d.largo("proyectoId"),
                "estado", d.textoObligatorio("estado"));
    }
}
