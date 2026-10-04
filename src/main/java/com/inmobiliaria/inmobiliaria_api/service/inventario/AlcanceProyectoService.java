package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlcanceProyectoService {

    private final JdbcTemplate jdbcTemplate;

    public AlcanceProyectoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AlcanceProyectos obtener(Long usuarioId, Long empresaId) {
        List<FilaAlcance> filas = jdbcTemplate.query("""
                SELECT tipo_alcance, proyecto_id
                FROM usuario_alcances
                WHERE usuario_id = ?
                  AND empresa_id = ?
                  AND vigente_desde <= CURRENT_TIMESTAMP(6)
                  AND (vigente_hasta IS NULL
                       OR vigente_hasta > CURRENT_TIMESTAMP(6))
                """,
                (resultSet, rowNumber) -> new FilaAlcance(
                        resultSet.getString("tipo_alcance"),
                        resultSet.getObject("proyecto_id", Long.class)
                ),
                usuarioId,
                empresaId
        );

        boolean empresaCompleta = filas.stream()
                .anyMatch(fila -> "EMPRESA".equals(fila.tipo()));
        boolean propios = filas.stream()
                .anyMatch(fila -> "PROPIOS".equals(fila.tipo()));
        Set<Long> proyectos = new LinkedHashSet<>();
        filas.stream()
                .filter(fila -> "PROYECTO".equals(fila.tipo()))
                .map(FilaAlcance::proyectoId)
                .filter(id -> id != null)
                .forEach(proyectos::add);
        return new AlcanceProyectos(
                empresaCompleta,
                propios,
                Set.copyOf(proyectos)
        );
    }

    @Transactional(readOnly = true)
    public void verificarEmpresa(Long usuarioId, Long empresaId) {
        if (!obtener(usuarioId, empresaId).empresaCompleta()) {
            throw new AccesoDenegadoException(
                    "La operación requiere alcance completo sobre la empresa"
            );
        }
    }

    @Transactional(readOnly = true)
    public void verificarProyecto(
            Long usuarioId,
            Long empresaId,
            Proyecto proyecto
    ) {
        if (!empresaId.equals(proyecto.getEmpresaId())) {
            throw new AccesoDenegadoException(
                    "El proyecto no pertenece a la empresa autenticada"
            );
        }
        AlcanceProyectos alcance = obtener(usuarioId, empresaId);
        if (!alcance.permite(
                proyecto.getId(),
                proyecto.getGerenteId(),
                usuarioId
        )) {
            throw new AccesoDenegadoException(
                    "El usuario no tiene alcance sobre el proyecto"
            );
        }
    }

    private record FilaAlcance(String tipo, Long proyectoId) {
    }
}
