package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code tickets_soporte} → {@code lectura_tickets_soporte}. La empresa es opcional, como en MySQL. */
@Component
public class ProyectorTicketsSoporte extends ProyectorTablaEspejo {

    public ProyectorTicketsSoporte(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "tickets_soporte", "lectura_tickets_soporte");
    }

    @Override
    Map<String, Object> columnas(LectorDocumento d) {
        return fila(
                "empresa_id", d.largo("empresaId"),
                "proyecto_id", d.largo("proyectoId"),
                "estado", d.textoObligatorio("estado"));
    }
}
