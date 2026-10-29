package com.inmobiliaria.inmobiliaria_api.service.compartido.migracion;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base de los migradores que copian una tabla de MySQL (y sus tablas hijas) a una colección
 * de MongoDB al arrancar la aplicación.
 *
 * <ul>
 *   <li>Idempotente: solo inserta los ids que aún no existen en MongoDB, así que no pisa lo que
 *       ya se modificó en la colección nueva y puede correr en cada arranque.</li>
 *   <li>Antes de copiar sube cada secuencia de {@code contadores} al id máximo de su tabla, para
 *       que un documento nuevo nunca reciba un id que ya usó MySQL.</li>
 *   <li>Recorre la tabla por lotes de {@value #LOTE} filas ordenadas por id.</li>
 *   <li>Las fechas se convierten con el conversor de Spring Data, así un documento migrado se lee
 *       igual que uno creado por la aplicación. Los campos nulos no se escriben, como hace Spring.</li>
 * </ul>
 */
public abstract class MigradorTablaMongo implements ApplicationRunner {

    public static final int LOTE = 500;

    /**
     * Spring Data guarda la propiedad {@code id} de los objetos embebidos como {@code _id}
     * (igual que en la raíz); los migradores escriben lo mismo para que se lean igual.
     */
    protected static final String ID_EMBEBIDO = "_id";

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final JdbcTemplate jdbcTemplate;
    private final DocumentosMigradosRepository documentos;
    private final SecuenciaService secuencias;

    protected MigradorTablaMongo(JdbcTemplate jdbcTemplate,
                                 DocumentosMigradosRepository documentos,
                                 SecuenciaService secuencias) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentos = documentos;
        this.secuencias = secuencias;
    }

    /** Colección de destino. */
    protected abstract String coleccion();

    /** Clase Java del documento (se guarda en {@code _class}, como hace Spring Data). */
    protected abstract Class<?> claseDocumento();

    /** Secuencia de {@code contadores} → tabla de MySQL cuyo MAX(id) es su mínimo. */
    protected abstract Map<String, String> secuencias();

    /**
     * Consulta de la tabla raíz. Debe terminar en {@code WHERE id > ? ORDER BY id LIMIT ?}
     * (o filtros equivalentes con esos dos parámetros en ese orden).
     */
    protected abstract String consultaRaiz();

    /** Convierte una fila de la tabla raíz en documento (sin {@code _id} ni {@code _class}). */
    protected abstract Document raiz(ResultSet rs) throws SQLException;

    /** Agrega lo embebido a los documentos del lote; las llaves son los ids de la raíz. */
    protected void completar(Map<Long, Document> porId) {
    }

    /** Crea índices u otra preparación de la colección; se llama antes de copiar. */
    protected void prepararColeccion() {
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrados = migrar();
        if (migrados > 0) {
            log.info("Se copiaron {} documentos de MySQL a la colección {} de MongoDB", migrados, coleccion());
        }
    }

    /** @return cuántos documentos se insertaron en MongoDB en esta ejecución */
    public int migrar() {
        prepararColeccion();
        secuencias().forEach((secuencia, tabla) -> secuencias.asegurarMinimo(secuencia, maximo(tabla)));
        int migrados = 0;
        long ultimoId = 0;
        while (true) {
            List<Document> lote = jdbcTemplate.query(consultaRaiz(), (rs, fila) -> {
                Document documento = new Document("_id", rs.getLong("id"));
                documento.put("_class", claseDocumento().getName());
                documento.putAll(raiz(rs));
                return documento;
            }, ultimoId, LOTE);
            if (lote.isEmpty()) {
                return migrados;
            }
            ultimoId = id(lote.get(lote.size() - 1));
            Set<Long> existentes = documentos.idsExistentes(coleccion(), lote.stream().map(MigradorTablaMongo::id).toList());
            Map<Long, Document> faltantes = new LinkedHashMap<>();
            lote.stream().filter(d -> !existentes.contains(id(d))).forEach(d -> faltantes.put(id(d), d));
            if (faltantes.isEmpty()) {
                continue;
            }
            completar(faltantes);
            migrados += documentos.insertar(coleccion(), new ArrayList<>(faltantes.values()));
        }
    }

    /**
     * Lee filas hijas agrupadas por la columna {@code padre}. La consulta lleva {@code %s} donde
     * van los marcadores del {@code IN} y debe incluir la columna {@code padre}.
     */
    protected Map<Long, List<Document>> agrupar(String sql, String padre, Collection<Long> ids, Fila fila) {
        Map<Long, List<Document>> grupos = new HashMap<>();
        if (ids.isEmpty()) {
            return grupos;
        }
        String marcadores = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbcTemplate.query(sql.formatted(marcadores), rs -> {
            grupos.computeIfAbsent(rs.getLong(padre), id -> new ArrayList<>()).add(fila.leer(rs));
        }, ids.toArray());
        return grupos;
    }

    protected Object fecha(ResultSet rs, String columna) throws SQLException {
        return documentos.valorMongo(rs.getObject(columna, LocalDateTime.class));
    }

    /** Columna DATE, convertida igual que un {@code LocalDate} de Spring Data. */
    protected Object dia(ResultSet rs, String columna) throws SQLException {
        return documentos.valorMongo(rs.getObject(columna, LocalDate.class));
    }

    /**
     * Monto para un campo marcado con {@code @Field(targetType = DECIMAL128)}: se escribe como
     * Decimal128 (el conversor por defecto de Spring lo guardaría como texto).
     */
    protected static Decimal128 decimal(ResultSet rs, String columna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(columna);
        return valor == null ? null : new Decimal128(valor);
    }

    protected static Long largo(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    protected static void poner(Map<String, Object> documento, String campo, Object valor) {
        if (valor != null) {
            documento.put(campo, valor);
        }
    }

    private long maximo(String tabla) {
        Long maximo = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class);
        return maximo == null ? 0 : maximo;
    }

    private static long id(Document documento) {
        return documento.get("_id", Number.class).longValue();
    }

    /** Convierte una fila hija en documento embebido. */
    @FunctionalInterface
    protected interface Fila {
        Document leer(ResultSet rs) throws SQLException;
    }
}
