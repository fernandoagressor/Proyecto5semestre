package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
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
 * Copia a MongoDB el historial de {@code intentos_acceso} que aún no está en la colección
 * (rama 7), conservando ids. Se desactiva con {@code app.migracion.auditoria.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.auditoria", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorIntentosAcceso extends MigradorTablaMongo {

    private final IntentoAccesoRepository repository;

    public MigradorIntentosAcceso(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                  IntentoAccesoRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return IntentoAcceso.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return IntentoAcceso.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(LoginAuditService.SECUENCIA_INTENTOS, "intentos_acceso");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, usuario_id, identificador, resultado, motivo, ip_origen, agente_usuario, fecha_intento
                FROM intentos_acceso
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "usuarioId", largo(rs, "usuario_id"));
        poner(d, "identificador", rs.getString("identificador"));
        poner(d, "resultado", rs.getString("resultado"));
        poner(d, "motivo", rs.getString("motivo"));
        poner(d, "ipOrigen", rs.getString("ip_origen"));
        poner(d, "agenteUsuario", rs.getString("agente_usuario"));
        poner(d, "fechaIntento", fecha(rs, "fecha_intento"));
        return d;
    }
}
