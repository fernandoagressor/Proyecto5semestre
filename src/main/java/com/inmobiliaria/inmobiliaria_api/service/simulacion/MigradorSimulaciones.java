package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copia a MongoDB las simulaciones que siguen solo en MySQL (rama 8b), con su cronograma de
 * {@code simulacion_cuotas} embebido. Toma el asesor de la oportunidad para filtrar por alcance.
 * Se desactiva con {@code app.migracion.simulaciones.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.simulaciones", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorSimulaciones extends MigradorTablaMongo {

    private final SimulacionRepository repository;

    public MigradorSimulaciones(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                SimulacionRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return Simulacion.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return Simulacion.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SimulacionService.SECUENCIA, "simulaciones");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT s.id, s.empresa_id, s.proyecto_id, s.codigo, s.oportunidad_id, o.asesor_id, s.lote_id,
                       s.creado_por, s.valor_lote, s.descuento, s.cuota_inicial, s.saldo_financiar,
                       s.numero_cuotas, s.tasa_efectiva_anual, s.periodicidad, s.vigente_hasta, s.estado,
                       s.version, s.creado_en, s.actualizado_en
                FROM simulaciones s
                LEFT JOIN oportunidades o ON o.id = s.oportunidad_id
                WHERE s.id > ?
                ORDER BY s.id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "proyectoId", largo(rs, "proyecto_id"));
        poner(d, "codigo", rs.getString("codigo"));
        poner(d, "oportunidadId", largo(rs, "oportunidad_id"));
        poner(d, "asesorId", largo(rs, "asesor_id"));
        poner(d, "loteId", largo(rs, "lote_id"));
        poner(d, "creadoPor", largo(rs, "creado_por"));
        poner(d, "valorLote", decimal(rs, "valor_lote"));
        poner(d, "descuento", decimal(rs, "descuento"));
        poner(d, "cuotaInicial", decimal(rs, "cuota_inicial"));
        poner(d, "saldoFinanciar", decimal(rs, "saldo_financiar"));
        d.put("numeroCuotas", rs.getInt("numero_cuotas"));
        poner(d, "tasaEfectivaAnual", decimal(rs, "tasa_efectiva_anual"));
        poner(d, "periodicidad", rs.getString("periodicidad"));
        poner(d, "vigenteHasta", fecha(rs, "vigente_hasta"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }

    @Override
    protected void completar(Map<Long, Document> porId) {
        Map<Long, List<Document>> cuotas = agrupar("""
                SELECT simulacion_id, numero, fecha_vencimiento, capital, interes, otros, valor_total,
                       saldo_proyectado
                FROM simulacion_cuotas
                WHERE simulacion_id IN (%s)
                ORDER BY simulacion_id, numero
                """, "simulacion_id", porId.keySet(), rs -> {
            Document c = new Document();
            c.put("simulacionId", rs.getLong("simulacion_id"));
            c.put("numero", rs.getInt("numero"));
            poner(c, "fechaVencimiento", dia(rs, "fecha_vencimiento"));
            poner(c, "capital", decimal(rs, "capital"));
            poner(c, "interes", decimal(rs, "interes"));
            poner(c, "otros", decimal(rs, "otros"));
            poner(c, "valorTotal", decimal(rs, "valor_total"));
            poner(c, "saldoProyectado", decimal(rs, "saldo_proyectado"));
            return c;
        });
        porId.forEach((id, documento) -> documento.put("cuotas", cuotas.getOrDefault(id, List.of())));
    }
}
