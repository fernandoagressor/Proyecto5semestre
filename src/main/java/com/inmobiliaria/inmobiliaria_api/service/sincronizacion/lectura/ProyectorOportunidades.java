package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code oportunidades} → {@code lectura_oportunidades}. Los valores en pesos llegan como Decimal128. */
@Component
public class ProyectorOportunidades extends ProyectorTablaEspejo {

    public ProyectorOportunidades(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "oportunidades", "lectura_oportunidades");
    }

    @Override
    Map<String, Object> columnas(LectorDocumento d) {
        return fila(
                "empresa_id", d.largoObligatorio("empresaId"),
                "proyecto_id", d.largoObligatorio("proyectoId"),
                "asesor_id", d.largoObligatorio("asesorId"),
                "etapa_actual", d.textoObligatorio("etapaActual"),
                "estado", d.textoObligatorio("estado"),
                "probabilidad_cierre", d.decimalObligatorio("probabilidadCierre"),
                "valor_estimado", d.decimal("valorEstimado"),
                "fecha_cierre", d.fecha("fechaCierre"),
                "creado_en", d.fechaObligatoria("creadoEn"));
    }
}
