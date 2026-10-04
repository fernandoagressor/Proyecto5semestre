package com.inmobiliaria.inmobiliaria_api.service.empresa;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModuloEmpresaService {

    public static final String MODULO_OBLIGATORIO = "ADMINISTRACION_SEGURIDAD";

    public static final List<String> MODULOS_DISPONIBLES = List.of(
            MODULO_OBLIGATORIO,
            "PROYECTOS_INVENTARIO",
            "CLIENTES_VENTAS",
            "RESERVAS_CONTRATOS",
            "CARTERA_PAGOS",
            "PORTAL_SERVICIO",
            "GERENCIA_REPORTES"
    );

    private static final Set<String> CATALOGO = Set.copyOf(MODULOS_DISPONIBLES);

    private final JdbcTemplate jdbcTemplate;

    public ModuloEmpresaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> normalizar(List<String> modulos) {
        if (modulos == null || modulos.isEmpty()) {
            return MODULOS_DISPONIBLES;
        }
        LinkedHashSet<String> resultado = new LinkedHashSet<>();
        resultado.add(MODULO_OBLIGATORIO);
        for (String modulo : modulos) {
            if (modulo == null || modulo.isBlank()) {
                continue;
            }
            String codigo = modulo.trim().toUpperCase(Locale.ROOT);
            if (!CATALOGO.contains(codigo)) {
                throw new ReglaNegocioException("El módulo " + codigo + " no está disponible");
            }
            resultado.add(codigo);
        }
        return new ArrayList<>(resultado);
    }

    public void guardar(Long empresaId, List<String> modulos) {
        List<String> activos = normalizar(modulos);
        jdbcTemplate.update("DELETE FROM empresa_modulos WHERE empresa_id = ?", empresaId);
        for (String codigo : activos) {
            jdbcTemplate.update("""
                    INSERT INTO empresa_modulos (empresa_id, codigo, activo)
                    VALUES (?, ?, 1)
                    """, empresaId, codigo);
        }
    }

    public List<String> activos(Long empresaId) {
        List<String> modulos = jdbcTemplate.queryForList("""
                SELECT codigo
                FROM empresa_modulos
                WHERE empresa_id = ? AND activo = 1
                ORDER BY id
                """, String.class, empresaId);
        return modulos.isEmpty() ? MODULOS_DISPONIBLES : List.copyOf(modulos);
    }
}
