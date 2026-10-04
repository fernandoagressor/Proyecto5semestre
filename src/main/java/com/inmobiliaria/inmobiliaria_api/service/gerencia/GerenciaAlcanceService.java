package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GerenciaAlcanceService {
    private final AlcanceProyectoService alcanceProyectoService;
    private final JdbcTemplate jdbcTemplate;

    public GerenciaAlcanceService(AlcanceProyectoService alcanceProyectoService,
                                  JdbcTemplate jdbcTemplate) {
        this.alcanceProyectoService = alcanceProyectoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AlcanceProyectos obtener(ContextoUsuario contexto) {
        return alcanceProyectoService.obtener(contexto.usuarioId(), contexto.empresaId());
    }

    @Transactional(readOnly = true)
    public void verificarProyecto(ContextoUsuario contexto, Long proyectoId) {
        if (proyectoId == null) return;
        FilaProyecto proyecto = jdbcTemplate.query("""
                SELECT id, gerente_id FROM proyectos
                WHERE id = ? AND empresa_id = ?
                """, rs -> rs.next()
                ? new FilaProyecto(rs.getLong("id"), rs.getObject("gerente_id", Long.class))
                : null, proyectoId, contexto.empresaId());
        if (proyecto == null) {
            throw new ReglaNegocioException("El proyecto no pertenece a la empresa autenticada");
        }
        AlcanceProyectos alcance = obtener(contexto);
        if (!alcance.permite(proyecto.id(), proyecto.gerenteId(), contexto.usuarioId())) {
            throw new AccesoDenegadoException("El usuario no tiene alcance sobre el proyecto");
        }
    }

    @Transactional(readOnly = true)
    public Long proyectoParaConsulta(ContextoUsuario contexto, Long proyectoId,
                                     boolean permitirRegistrosPropios) {
        AlcanceProyectos alcance = obtener(contexto);
        if (proyectoId != null) {
            verificarProyecto(contexto, proyectoId);
            return proyectoId;
        }
        if (alcance.empresaCompleta()) return null;
        if (alcance.proyectos().size() == 1) return alcance.proyectos().iterator().next();
        if (permitirRegistrosPropios && alcance.registrosPropios()) return null;
        throw new AccesoDenegadoException(
                "Debe seleccionar uno de los proyectos incluidos en su alcance"
        );
    }

    @Transactional(readOnly = true)
    public void verificarEmpresa(ContextoUsuario contexto) {
        alcanceProyectoService.verificarEmpresa(contexto.usuarioId(), contexto.empresaId());
    }

    @Transactional(readOnly = true)
    public void verificarUsuarioEmpresa(ContextoUsuario contexto, Long usuarioId) {
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM usuarios
                WHERE id = ? AND empresa_id = ? AND activo = 1
                """, Integer.class, usuarioId, contexto.empresaId());
        if (total == null || total == 0) {
            throw new ReglaNegocioException("El usuario no existe, está inactivo o pertenece a otra empresa");
        }
    }

    @Transactional(readOnly = true)
    public void verificarArchivo(ContextoUsuario contexto, Long archivoId) {
        if (archivoId == null) return;
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM archivos
                WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'
                """, Integer.class, archivoId, contexto.empresaId());
        if (total == null || total == 0) {
            throw new ReglaNegocioException("El archivo no existe o no está activo en la empresa");
        }
    }

    private record FilaProyecto(Long id, Long gerenteId) { }
}
