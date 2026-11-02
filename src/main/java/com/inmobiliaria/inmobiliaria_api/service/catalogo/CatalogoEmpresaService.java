package com.inmobiliaria.inmobiliaria_api.service.catalogo;

import com.inmobiliaria.inmobiliaria_api.dto.catalogo.CatalogoDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publicación del catálogo de la empresa y galería de fotos de sus proyectos. */
@Service
public class CatalogoEmpresaService {

    private static final int MAX_IMAGENES = 20;

    private final JdbcTemplate jdbc;
    private final AuditoriaRegistroService auditoria;

    public CatalogoEmpresaService(JdbcTemplate jdbc, AuditoriaRegistroService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public CatalogoDtos.EstadoCatalogo estado(ContextoUsuario ctx) {
        return jdbc.queryForObject("SELECT codigo, catalogo_publico FROM empresas WHERE id = ?",
                (rs, n) -> new CatalogoDtos.EstadoCatalogo(rs.getString("codigo"), rs.getBoolean("catalogo_publico")),
                ctx.empresaId());
    }

    @Transactional
    public CatalogoDtos.EstadoCatalogo cambiar(ContextoUsuario ctx, boolean publico) {
        jdbc.update("UPDATE empresas SET catalogo_publico = ? WHERE id = ?", publico, ctx.empresaId());
        auditoria.registrar(ctx, null, "PROYECTOS", publico ? "CATALOGO_PUBLICADO" : "CATALOGO_RETIRADO",
                "EMPRESA", ctx.empresaId(), publico ? "Catálogo público activado" : "Catálogo público desactivado",
                null, null);
        return estado(ctx);
    }

    @Transactional(readOnly = true)
    public List<CatalogoDtos.Imagen> imagenes(ContextoUsuario ctx, Long proyectoId) {
        verificarProyecto(ctx, proyectoId);
        return jdbc.query("""
                SELECT id, url, titulo FROM proyecto_imagenes
                WHERE empresa_id = ? AND proyecto_id = ? ORDER BY orden, id
                """, (rs, n) -> new CatalogoDtos.Imagen(rs.getLong("id"), rs.getString("url"), rs.getString("titulo")),
                ctx.empresaId(), proyectoId);
    }

    @Transactional
    public CatalogoDtos.Imagen agregar(ContextoUsuario ctx, Long proyectoId, CatalogoDtos.ImagenRequest request) {
        verificarProyecto(ctx, proyectoId);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM proyecto_imagenes WHERE empresa_id = ? AND proyecto_id = ?",
                Integer.class, ctx.empresaId(), proyectoId);
        if (total != null && total >= MAX_IMAGENES) {
            throw new ReglaNegocioException("Cada proyecto admite hasta " + MAX_IMAGENES + " fotos");
        }
        String titulo = request.titulo() == null || request.titulo().isBlank() ? null : request.titulo().trim();
        jdbc.update("""
                INSERT INTO proyecto_imagenes (empresa_id, proyecto_id, url, titulo, orden, creado_por)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ctx.empresaId(), proyectoId, request.url().trim(), titulo, total == null ? 0 : total, ctx.usuarioId());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditoria.registrar(ctx, proyectoId, "PROYECTOS", "FOTO_AGREGADA", "PROYECTO", proyectoId,
                "Foto agregada a la galería", null, request.url());
        return new CatalogoDtos.Imagen(id, request.url().trim(), titulo);
    }

    @Transactional
    public void eliminar(ContextoUsuario ctx, Long proyectoId, Long imagenId) {
        verificarProyecto(ctx, proyectoId);
        int borradas = jdbc.update("DELETE FROM proyecto_imagenes WHERE id = ? AND proyecto_id = ? AND empresa_id = ?",
                imagenId, proyectoId, ctx.empresaId());
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("La foto no existe");
        }
        auditoria.registrar(ctx, proyectoId, "PROYECTOS", "FOTO_ELIMINADA", "PROYECTO", proyectoId,
                "Foto eliminada de la galería", imagenId, null);
    }

    private void verificarProyecto(ContextoUsuario ctx, Long proyectoId) {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM proyectos WHERE id = ? AND empresa_id = ?",
                Integer.class, proyectoId, ctx.empresaId());
        if (existe == null || existe == 0) {
            throw new RecursoNoEncontradoException("El proyecto no existe");
        }
    }
}
