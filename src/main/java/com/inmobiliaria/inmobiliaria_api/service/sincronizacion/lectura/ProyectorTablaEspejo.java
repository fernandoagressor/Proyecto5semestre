package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.ProyectorLectura;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyecta una colección de MongoDB a su tabla espejo en MySQL.
 *
 * <p>Es idempotente, como exige el despachador: una inserción o actualización hace un upsert de la
 * fila completa con el estado actual del documento, y una eliminación (o un documento que ya no
 * existe) borra la fila. Aplicar dos veces el mismo cambio deja el mismo resultado.</p>
 */
public abstract class ProyectorTablaEspejo implements ProyectorLectura {

    private final JdbcTemplate jdbcTemplate;
    private final String coleccion;
    private final String tabla;

    protected ProyectorTablaEspejo(JdbcTemplate jdbcTemplate, String coleccion, String tabla) {
        this.jdbcTemplate = jdbcTemplate;
        this.coleccion = coleccion;
        this.tabla = tabla;
    }

    @Override
    public String coleccion() {
        return coleccion;
    }

    @Override
    public Optional<String> tablaEspejo() {
        return Optional.of(tabla);
    }

    @Override
    @Transactional
    public void aplicar(CambioDocumento cambio) {
        if (cambio.id() == null) {
            return;
        }
        boolean eliminado = cambio.tipo() == TipoCambio.ELIMINACION || cambio.datos().isEmpty();
        LectorDocumento documento = new LectorDocumento(coleccion, cambio.id(), cambio.datos());
        if (eliminado) {
            borrarFila(tabla, "id", cambio.id());
        } else {
            guardarFila(tabla, "id", cambio.id(), columnas(documento));
        }
        proyectarDependientes(cambio.id(), documento, eliminado);
    }

    /** Columnas de la fila (sin el id) a partir del documento, en el orden en que se insertan. */
    abstract Map<String, Object> columnas(LectorDocumento documento);

    /** Para documentos con partes embebidas que tienen su propia tabla espejo (p. ej. la encuesta de una PQRS). */
    void proyectarDependientes(Long id, LectorDocumento documento, boolean eliminado) {
    }

    void guardarFila(String tablaDestino, String columnaClave, Object clave, Map<String, Object> columnas) {
        List<String> nombres = new ArrayList<>(columnas.keySet());
        List<Object> valores = new ArrayList<>();
        valores.add(clave);
        valores.addAll(columnas.values());
        String sql = "INSERT INTO " + tablaDestino + " (" + columnaClave + ", " + String.join(", ", nombres) + ")"
                + " VALUES (" + String.join(", ", Collections.nCopies(nombres.size() + 1, "?")) + ") AS nuevo"
                + " ON DUPLICATE KEY UPDATE "
                + nombres.stream().map(c -> c + " = nuevo." + c).collect(Collectors.joining(", "));
        jdbcTemplate.update(sql, valores.toArray());
    }

    void borrarFila(String tablaDestino, String columnaClave, Object clave) {
        jdbcTemplate.update("DELETE FROM " + tablaDestino + " WHERE " + columnaClave + " = ?", clave);
    }

    /** Arma el mapa de columnas conservando el orden. */
    static Map<String, Object> fila(Object... columnaYValor) {
        Map<String, Object> fila = new LinkedHashMap<>();
        for (int i = 0; i < columnaYValor.length; i += 2) {
            fila.put((String) columnaYValor[i], columnaYValor[i + 1]);
        }
        return fila;
    }
}
