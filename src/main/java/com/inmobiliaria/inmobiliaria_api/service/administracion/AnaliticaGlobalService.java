package com.inmobiliaria.inmobiliaria_api.service.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnaliticaGlobalService {

    private final JdbcTemplate jdbcTemplate;

    public AnaliticaGlobalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdministracionDtos.AnaliticaGlobalResponse obtener() {
        List<AdministracionDtos.EmpresaAnaliticaGlobalResponse> empresas = jdbcTemplate.query("""
                SELECT e.id, e.codigo, e.nombre, e.estado,
                       (SELECT COUNT(*) FROM proyectos p WHERE p.empresa_id = e.id) proyectos,
                       (SELECT COUNT(*) FROM usuarios u WHERE u.empresa_id = e.id) usuarios,
                       (SELECT COUNT(*) FROM lotes l WHERE l.empresa_id = e.id) lotes,
                       (SELECT COUNT(*) FROM reservas r
                        WHERE r.empresa_id = e.id AND r.estado = 'ACTIVA') reservas_activas,
                       (SELECT COUNT(*) FROM contratos c
                        WHERE c.empresa_id = e.id AND c.estado = 'VIGENTE') contratos_vigentes,
                       (SELECT COALESCE(SUM(c.valor_total), 0) FROM contratos c
                        WHERE c.empresa_id = e.id
                          AND c.estado IN ('VIGENTE', 'FINALIZADO')) ventas,
                       (SELECT COALESCE(SUM(p.valor), 0) FROM pagos p
                        WHERE p.empresa_id = e.id AND p.estado = 'CONFIRMADO') recaudo,
                       (SELECT COALESCE(SUM(q.saldo + q.interes_mora), 0) FROM cuotas q
                        WHERE q.empresa_id = e.id
                          AND q.estado NOT IN ('PAGADA', 'CANCELADA', 'CONDONADA')) cartera,
                       (SELECT COALESCE(SUM(q.saldo + q.interes_mora), 0) FROM cuotas q
                        WHERE q.empresa_id = e.id
                          AND q.estado NOT IN ('PAGADA', 'CANCELADA', 'CONDONADA')
                          AND (q.estado = 'VENCIDA'
                               OR q.fecha_vencimiento < CURRENT_DATE())) cartera_vencida
                FROM empresas e
                ORDER BY ventas DESC, e.nombre ASC
                """, (rs, row) -> new AdministracionDtos.EmpresaAnaliticaGlobalResponse(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("estado"),
                rs.getLong("proyectos"),
                rs.getLong("usuarios"),
                rs.getLong("lotes"),
                rs.getLong("reservas_activas"),
                rs.getLong("contratos_vigentes"),
                dinero(rs.getBigDecimal("ventas")),
                dinero(rs.getBigDecimal("recaudo")),
                dinero(rs.getBigDecimal("cartera")),
                dinero(rs.getBigDecimal("cartera_vencida"))
        ));

        List<AdministracionDtos.SerieAnaliticaGlobalResponse> lotesPorEstado = series("""
                SELECT estado categoria, CAST(COUNT(*) AS DECIMAL(19, 2)) valor,
                       COUNT(*) cantidad
                FROM lotes GROUP BY estado ORDER BY cantidad DESC
                """);
        List<AdministracionDtos.SerieAnaliticaGlobalResponse> proyectosPorEstado = series("""
                SELECT estado categoria, CAST(COUNT(*) AS DECIMAL(19, 2)) valor,
                       COUNT(*) cantidad
                FROM proyectos GROUP BY estado ORDER BY cantidad DESC
                """);
        List<AdministracionDtos.SerieAnaliticaGlobalResponse> ventasMensuales = series("""
                SELECT DATE_FORMAT(fecha_firma, '%Y-%m') categoria,
                       COALESCE(SUM(valor_total), 0) valor, COUNT(*) cantidad
                FROM contratos
                WHERE estado IN ('VIGENTE', 'FINALIZADO')
                  AND fecha_firma >= DATE_SUB(
                      DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01'), INTERVAL 5 MONTH
                  )
                GROUP BY DATE_FORMAT(fecha_firma, '%Y-%m') ORDER BY categoria
                """);
        List<AdministracionDtos.SerieAnaliticaGlobalResponse> recaudoMensual = series("""
                SELECT DATE_FORMAT(fecha_pago, '%Y-%m') categoria,
                       COALESCE(SUM(valor), 0) valor, COUNT(*) cantidad
                FROM pagos
                WHERE estado = 'CONFIRMADO'
                  AND fecha_pago >= DATE_SUB(
                      DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01'), INTERVAL 5 MONTH
                  )
                GROUP BY DATE_FORMAT(fecha_pago, '%Y-%m') ORDER BY categoria
                """);

        return new AdministracionDtos.AnaliticaGlobalResponse(
                sumar(empresas.stream().map(AdministracionDtos.EmpresaAnaliticaGlobalResponse::ventas).toList()),
                sumar(empresas.stream().map(AdministracionDtos.EmpresaAnaliticaGlobalResponse::recaudo).toList()),
                sumar(empresas.stream().map(AdministracionDtos.EmpresaAnaliticaGlobalResponse::cartera).toList()),
                sumar(empresas.stream().map(AdministracionDtos.EmpresaAnaliticaGlobalResponse::carteraVencida).toList()),
                dinero(jdbcTemplate.queryForObject("""
                        SELECT COALESCE(SUM(valor_lista), 0)
                        FROM lotes WHERE estado = 'DISPONIBLE'
                        """, BigDecimal.class)),
                empresas.stream().mapToLong(AdministracionDtos.EmpresaAnaliticaGlobalResponse::lotes).sum(),
                cantidad(lotesPorEstado, "DISPONIBLE"),
                cantidad(lotesPorEstado, "RESERVADO"),
                cantidad(lotesPorEstado, "VENDIDO"),
                cantidad(lotesPorEstado, "BLOQUEADO"),
                empresas.stream().mapToLong(AdministracionDtos.EmpresaAnaliticaGlobalResponse::reservasActivas).sum(),
                empresas.stream().mapToLong(AdministracionDtos.EmpresaAnaliticaGlobalResponse::contratosVigentes).sum(),
                empresas,
                lotesPorEstado,
                proyectosPorEstado,
                ventasMensuales,
                recaudoMensual
        );
    }

    private List<AdministracionDtos.SerieAnaliticaGlobalResponse> series(String sql) {
        return jdbcTemplate.query(sql, (rs, row) ->
                new AdministracionDtos.SerieAnaliticaGlobalResponse(
                        rs.getString("categoria"),
                        dinero(rs.getBigDecimal("valor")),
                        rs.getLong("cantidad")
                ));
    }

    private static long cantidad(
            List<AdministracionDtos.SerieAnaliticaGlobalResponse> series,
            String categoria
    ) {
        return series.stream()
                .filter(serie -> categoria.equals(serie.categoria()))
                .mapToLong(AdministracionDtos.SerieAnaliticaGlobalResponse::cantidad)
                .sum();
    }

    private static BigDecimal sumar(List<BigDecimal> valores) {
        return dinero(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
