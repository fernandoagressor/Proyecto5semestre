package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuentaGenerado;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.EstadoCuentaRepository;
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
 * Copia a MongoDB los estados de cuenta emitidos que siguen solo en MySQL (rama 9).
 * Se desactiva con {@code app.migracion.seguimiento.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.seguimiento", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorEstadosCuenta extends MigradorTablaMongo {

    private final EstadoCuentaRepository repository;

    public MigradorEstadosCuenta(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                 EstadoCuentaRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return EstadoCuentaGenerado.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return EstadoCuentaGenerado.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(CarteraGestionService.SECUENCIA_ESTADOS_CUENTA, "estados_cuenta_generados");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, contrato_id, fecha_corte, saldo_capital, saldo_mora, saldo_total, archivo_id,
                       generado_por, creado_en
                FROM estados_cuenta_generados
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "contratoId", largo(rs, "contrato_id"));
        poner(d, "fechaCorte", dia(rs, "fecha_corte"));
        poner(d, "saldoCapital", decimal(rs, "saldo_capital"));
        poner(d, "saldoMora", decimal(rs, "saldo_mora"));
        poner(d, "saldoTotal", decimal(rs, "saldo_total"));
        poner(d, "archivoId", largo(rs, "archivo_id"));
        poner(d, "generadoPor", largo(rs, "generado_por"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        return d;
    }
}
