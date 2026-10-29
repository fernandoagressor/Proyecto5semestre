package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.GestionCobroRepository;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copia a MongoDB las gestiones de cobro que siguen solo en MySQL (rama 9). Toma el proyecto del
 * contrato para el filtro del dashboard. Se desactiva con {@code app.migracion.seguimiento.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.seguimiento", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorGestionesCobro extends MigradorTablaMongo {

    private final GestionCobroRepository repository;

    public MigradorGestionesCobro(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                  GestionCobroRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return GestionCobro.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return GestionCobro.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(CarteraGestionService.SECUENCIA_GESTIONES, "gestiones_cobro");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT g.id, g.empresa_id, c.proyecto_id, g.contrato_id, g.cuota_id, g.gestor_id, g.canal,
                       g.resultado, g.detalle, g.promesa_pago_fecha, g.promesa_pago_valor, g.fecha_gestion,
                       g.creado_en
                FROM gestiones_cobro g
                LEFT JOIN contratos c ON c.id = g.contrato_id
                WHERE g.id > ?
                ORDER BY g.id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "proyectoId", largo(rs, "proyecto_id"));
        poner(d, "contratoId", largo(rs, "contrato_id"));
        poner(d, "cuotaId", largo(rs, "cuota_id"));
        poner(d, "gestorId", largo(rs, "gestor_id"));
        poner(d, "canal", rs.getString("canal"));
        poner(d, "resultado", rs.getString("resultado"));
        poner(d, "detalle", rs.getString("detalle"));
        poner(d, "promesaPagoFecha", dia(rs, "promesa_pago_fecha"));
        poner(d, "promesaPagoValor", decimal(rs, "promesa_pago_valor"));
        poner(d, "fechaGestion", fecha(rs, "fecha_gestion"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        return d;
    }
}
