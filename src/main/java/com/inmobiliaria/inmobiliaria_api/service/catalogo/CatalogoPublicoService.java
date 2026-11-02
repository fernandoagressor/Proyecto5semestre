package com.inmobiliaria.inmobiliaria_api.service.catalogo;

import com.inmobiliaria.inmobiliaria_api.dto.catalogo.CatalogoDtos;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.DemasiadosIntentosException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.ProspectoService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo público de una empresa (sin sesión). Solo responde si la empresa está ACTIVA y decidió
 * publicar su catálogo; muestra proyectos ACTIVOS y lotes DISPONIBLES, nunca datos de clientes.
 */
@Service
public class CatalogoPublicoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoPublicoService.class);
    private static final int MAX_CONTACTOS_POR_IP = 5;
    private static final Duration VENTANA = Duration.ofHours(1);
    private static final String PREFIJO = "nexora:catalogo:contacto:";
    private static final String NO_EXISTE = "El catálogo no existe o no está publicado";

    private final JdbcTemplate jdbc;
    private final ProspectoService prospectos;
    private final StringRedisTemplate redis;

    public CatalogoPublicoService(JdbcTemplate jdbc, ProspectoService prospectos, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.prospectos = prospectos;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    public CatalogoDtos.Empresa empresa(String codigo) {
        Long empresaId = empresaPublicada(codigo);
        List<CatalogoDtos.ProyectoResumen> proyectos = jdbc.query(RESUMEN_SQL + " ORDER BY p.nombre",
                (rs, n) -> resumen(rs), empresaId);
        return jdbc.queryForObject("""
                SELECT codigo, nombre, logo_url, ciudad, telefono, correo FROM empresas WHERE id = ?
                """, (rs, n) -> new CatalogoDtos.Empresa(rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("logo_url"), rs.getString("ciudad"), rs.getString("telefono"),
                rs.getString("correo"), proyectos), empresaId);
    }

    @Transactional(readOnly = true)
    public CatalogoDtos.ProyectoDetalle proyecto(String codigo, Long proyectoId) {
        Long empresaId = empresaPublicada(codigo);
        List<CatalogoDtos.ProyectoResumen> encontrados = jdbc.query(RESUMEN_SQL + " AND p.id = ?",
                (rs, n) -> resumen(rs), empresaId, proyectoId);
        if (encontrados.isEmpty()) {
            throw new RecursoNoEncontradoException("El proyecto no existe o no está publicado");
        }
        String direccion = jdbc.queryForObject("SELECT direccion FROM proyectos WHERE id = ?", String.class, proyectoId);
        List<CatalogoDtos.Imagen> imagenes = jdbc.query("""
                SELECT id, url, titulo FROM proyecto_imagenes
                WHERE empresa_id = ? AND proyecto_id = ? ORDER BY orden, id LIMIT 20
                """, (rs, n) -> new CatalogoDtos.Imagen(rs.getLong("id"), rs.getString("url"), rs.getString("titulo")),
                empresaId, proyectoId);
        List<CatalogoDtos.Lote> lotes = jdbc.query("""
                SELECT l.id, l.codigo, l.numero, e.nombre etapa, m.nombre manzana, l.area_m2, l.frente_m,
                       l.fondo_m, l.valor_lista, l.ubicacion_referencia
                FROM lotes l
                JOIN etapas_proyecto e ON e.id = l.etapa_id
                JOIN manzanas m ON m.id = l.manzana_id
                WHERE l.empresa_id = ? AND l.proyecto_id = ? AND l.estado = 'DISPONIBLE'
                ORDER BY e.secuencia, m.secuencia, CAST(l.numero AS UNSIGNED), l.numero
                LIMIT 500
                """, (rs, n) -> new CatalogoDtos.Lote(rs.getLong("id"), rs.getString("codigo"), rs.getString("numero"),
                rs.getString("etapa"), rs.getString("manzana"), rs.getBigDecimal("area_m2"),
                rs.getBigDecimal("frente_m"), rs.getBigDecimal("fondo_m"), rs.getBigDecimal("valor_lista"),
                rs.getString("ubicacion_referencia")), empresaId, proyectoId);
        return new CatalogoDtos.ProyectoDetalle(encontrados.get(0), direccion, imagenes, lotes);
    }

    /** Registra el contacto como prospecto de la empresa (fuente WEB), asignado al gerente del proyecto. */
    @Transactional
    public void contactar(String codigo, CatalogoDtos.Contacto contacto, String ip) {
        Long empresaId = empresaPublicada(codigo);
        limitar(ip);
        Long proyectoId = null;
        Long asesorId = null;
        if (contacto.proyectoId() != null) {
            List<Long[]> proyecto = jdbc.query("""
                    SELECT p.id, u.id gerente
                    FROM proyectos p
                    LEFT JOIN usuarios u ON u.id = p.gerente_id AND u.empresa_id = p.empresa_id AND u.estado = 'ACTIVO'
                    WHERE p.id = ? AND p.empresa_id = ? AND p.estado = 'ACTIVO'
                    """, (rs, n) -> new Long[] {rs.getLong("id"), rs.getObject("gerente", Long.class)},
                    contacto.proyectoId(), empresaId);
            if (proyecto.isEmpty()) {
                throw new RecursoNoEncontradoException("El proyecto no existe o no está publicado");
            }
            proyectoId = proyecto.get(0)[0];
            asesorId = proyecto.get(0)[1];
        }
        String mensaje = contacto.mensaje() == null || contacto.mensaje().isBlank()
                ? "Contacto desde el catálogo público"
                : "Mensaje desde el catálogo público: " + contacto.mensaje().trim();
        boolean nuevo = prospectos.registrarDesdeCatalogo(empresaId, proyectoId, asesorId, contacto.nombres(),
                contacto.telefono(), contacto.correo(), mensaje);
        log.info("Contacto del catálogo {} (empresa {}, proyecto {}, nuevo={})", codigo, empresaId, proyectoId, nuevo);
    }

    private Long empresaPublicada(String codigo) {
        if (codigo == null || !codigo.matches("[A-Za-z0-9_-]{1,30}")) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM empresas WHERE UPPER(codigo) = ? AND estado = 'ACTIVA' AND catalogo_publico = 1
                """, Long.class, codigo.toUpperCase(Locale.ROOT));
        if (ids.isEmpty()) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        return ids.get(0);
    }

    private void limitar(String ip) {
        try {
            String clave = PREFIJO + ip;
            Long total = redis.opsForValue().increment(clave);
            if (total != null && total == 1L) {
                redis.expire(clave, VENTANA);
            }
            if (total != null && total > MAX_CONTACTOS_POR_IP) {
                throw new DemasiadosIntentosException("Recibimos varios mensajes desde su red. Intente más tarde.");
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible: se omite el límite de contactos del catálogo", e);
        }
    }

    private static CatalogoDtos.ProyectoResumen resumen(ResultSet rs) throws SQLException {
        String descripcion = rs.getString("descripcion");
        if (descripcion != null && descripcion.length() > 600) {
            descripcion = descripcion.substring(0, 597) + "…";
        }
        return new CatalogoDtos.ProyectoResumen(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("ciudad"), rs.getString("departamento"), descripcion, rs.getString("imagen"),
                rs.getInt("porcentaje_avance"), rs.getLong("disponibles"), rs.getBigDecimal("precio_desde"),
                rs.getBigDecimal("area_min"), rs.getBigDecimal("area_max"));
    }

    private static final String RESUMEN_SQL = """
            SELECT p.id, p.codigo, p.nombre, p.ciudad, p.departamento, p.descripcion, p.porcentaje_avance,
                   COALESCE(p.imagen_principal_url, (SELECT i.url FROM proyecto_imagenes i
                       WHERE i.proyecto_id = p.id ORDER BY i.orden, i.id LIMIT 1)) imagen,
                   (SELECT COUNT(*) FROM lotes l WHERE l.proyecto_id = p.id AND l.estado = 'DISPONIBLE') disponibles,
                   (SELECT MIN(l.valor_lista) FROM lotes l WHERE l.proyecto_id = p.id AND l.estado = 'DISPONIBLE') precio_desde,
                   (SELECT MIN(l.area_m2) FROM lotes l WHERE l.proyecto_id = p.id AND l.estado = 'DISPONIBLE') area_min,
                   (SELECT MAX(l.area_m2) FROM lotes l WHERE l.proyecto_id = p.id AND l.estado = 'DISPONIBLE') area_max
            FROM proyectos p
            WHERE p.empresa_id = ? AND p.estado = 'ACTIVO'
            """;
}
