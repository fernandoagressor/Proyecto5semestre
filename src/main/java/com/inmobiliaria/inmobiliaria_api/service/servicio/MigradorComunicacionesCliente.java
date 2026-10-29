package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ComunicacionClienteRepository;
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
 * Copia a MongoDB las comunicaciones con clientes que siguen solo en MySQL (rama 10).
 * Se desactiva con {@code app.migracion.servicio.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.servicio", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorComunicacionesCliente extends MigradorTablaMongo {

    private final ComunicacionClienteRepository repository;

    public MigradorComunicacionesCliente(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
            ComunicacionClienteRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return ComunicacionCliente.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return ComunicacionCliente.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(ComunicacionClienteService.SECUENCIA, "comunicaciones_cliente");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, cliente_id, enviado_por, canal, asunto, contenido, es_masiva, estado, programada_para,
                       enviada_en, version, creado_en, actualizado_en
                FROM comunicaciones_cliente
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "clienteId", largo(rs, "cliente_id"));
        poner(d, "enviadoPor", largo(rs, "enviado_por"));
        poner(d, "canal", rs.getString("canal"));
        poner(d, "asunto", rs.getString("asunto"));
        poner(d, "contenido", rs.getString("contenido"));
        d.put("masiva", rs.getBoolean("es_masiva"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "programadaPara", fecha(rs, "programada_para"));
        poner(d, "enviadaEn", fecha(rs, "enviada_en"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
