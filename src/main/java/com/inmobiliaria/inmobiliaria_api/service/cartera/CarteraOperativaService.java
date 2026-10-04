package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.CarteraContratoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.CarteraResumenResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MetodoPagoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoContrato;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.MetodoPagoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarteraOperativaService {
    private final CarteraAlcanceService alcanceService;
    private final MetodoPagoRepository metodoPagoRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CarteraOperativaService(
            CarteraAlcanceService alcanceService,
            MetodoPagoRepository metodoPagoRepository,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.alcanceService = alcanceService;
        this.metodoPagoRepository = metodoPagoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CarteraContratoResponse> contratos(ContextoUsuario contexto) {
        List<Long> ids = alcanceService.contratosAutorizados(contexto).stream()
                .filter(contrato -> contrato.getEstado() == EstadoContrato.VIGENTE)
                .map(Contrato::getId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT
                  c.id AS contrato_id,
                  c.codigo AS contrato_codigo,
                  c.proyecto_id,
                  pr.nombre AS proyecto_nombre,
                  c.cliente_principal_id AS cliente_id,
                  TRIM(CONCAT(pe.nombres, ' ', COALESCE(pe.apellidos, ''))) AS cliente_nombre,
                  CONCAT(pe.tipo_documento, ' ', pe.numero_documento) AS cliente_documento,
                  c.lote_id,
                  l.codigo AS lote_codigo,
                  c.valor_total AS valor_contrato,
                  c.cuota_inicial,
                  c.saldo_financiado AS capital_financiado,
                  pp.id AS plan_pago_id,
                  pp.estado AS estado_plan,
                  COALESCE(pp.numero_cuotas, c.numero_cuotas) AS numero_cuotas,
                  COALESCE(SUM(CASE WHEN q.estado <> 'CANCELADA' THEN q.valor_programado ELSE 0 END), 0) AS total_programado,
                  COALESCE(SUM(CASE WHEN q.estado <> 'CANCELADA' THEN q.valor_pagado ELSE 0 END), 0) AS total_pagado,
                  COALESCE(SUM(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.saldo + q.interes_mora ELSE 0 END), 0) AS saldo_pendiente,
                  COALESCE(SUM(CASE WHEN q.estado = 'VENCIDA' THEN q.saldo + q.interes_mora ELSE 0 END), 0) AS saldo_vencido,
                  MIN(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.fecha_vencimiento END) AS proximo_vencimiento,
                  SUM(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL') THEN 1 ELSE 0 END) AS cuotas_pendientes,
                  SUM(CASE WHEN q.estado = 'VENCIDA' THEN 1 ELSE 0 END) AS cuotas_vencidas
                FROM contratos c
                JOIN proyectos pr ON pr.id = c.proyecto_id
                JOIN clientes cl ON cl.id = c.cliente_principal_id
                JOIN personas pe ON pe.id = cl.persona_id
                JOIN lotes l ON l.id = c.lote_id
                LEFT JOIN planes_pago pp
                  ON pp.contrato_id = c.id AND pp.estado = 'ACTIVO'
                LEFT JOIN cuotas q ON q.plan_pago_id = pp.id
                WHERE c.id IN (:contratoIds)
                  AND c.empresa_id = :empresaId
                  AND c.estado = 'VIGENTE'
                GROUP BY
                  c.id, c.codigo, c.proyecto_id, pr.nombre,
                  c.cliente_principal_id, pe.nombres, pe.apellidos,
                  pe.tipo_documento, pe.numero_documento,
                  c.lote_id, l.codigo, c.valor_total, c.cuota_inicial, c.saldo_financiado,
                  pp.id, pp.estado, pp.numero_cuotas, c.numero_cuotas
                ORDER BY proximo_vencimiento IS NULL, proximo_vencimiento, c.codigo
                """,
                new MapSqlParameterSource()
                        .addValue("contratoIds", ids)
                        .addValue("empresaId", contexto.empresaId()),
                (rs, rowNum) -> new CarteraContratoResponse(
                        rs.getLong("contrato_id"),
                        rs.getString("contrato_codigo"),
                        rs.getLong("proyecto_id"),
                        rs.getString("proyecto_nombre"),
                        rs.getLong("cliente_id"),
                        rs.getString("cliente_nombre"),
                        rs.getString("cliente_documento"),
                        rs.getLong("lote_id"),
                        rs.getString("lote_codigo"),
                        rs.getBigDecimal("valor_contrato"),
                        rs.getBigDecimal("cuota_inicial"),
                        rs.getBigDecimal("capital_financiado"),
                        rs.getObject("plan_pago_id", Long.class),
                        rs.getString("estado_plan"),
                        rs.getInt("numero_cuotas"),
                        dinero(rs.getBigDecimal("total_programado")),
                        dinero(rs.getBigDecimal("total_pagado")),
                        dinero(rs.getBigDecimal("saldo_pendiente")),
                        dinero(rs.getBigDecimal("saldo_vencido")),
                        fecha(rs.getDate("proximo_vencimiento")),
                        rs.getLong("cuotas_pendientes"),
                        rs.getLong("cuotas_vencidas")
                )
        );
    }

    @Transactional(readOnly = true)
    public CarteraResumenResponse resumen(ContextoUsuario contexto) {
        List<CarteraContratoResponse> contratos = contratos(contexto);
        if (contratos.isEmpty()) {
            return new CarteraResumenResponse(
                    0, 0, 0, dinero(BigDecimal.ZERO), dinero(BigDecimal.ZERO),
                    dinero(BigDecimal.ZERO), dinero(BigDecimal.ZERO),
                    dinero(BigDecimal.ZERO), 0, 0
            );
        }
        List<Long> ids = contratos.stream().map(CarteraContratoResponse::contratoId).toList();
        BigDecimal totalProgramado = suma(contratos.stream().map(CarteraContratoResponse::totalProgramado).toList());
        BigDecimal totalPagadoCuotas = suma(contratos.stream().map(CarteraContratoResponse::totalPagado).toList());
        BigDecimal saldo = suma(contratos.stream().map(CarteraContratoResponse::saldoPendiente).toList());
        BigDecimal vencida = suma(contratos.stream().map(CarteraContratoResponse::saldoVencido).toList());
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("contratoIds", ids)
                .addValue("empresaId", contexto.empresaId())
                .addValue("hoy", LocalDate.now())
                .addValue("limite", LocalDate.now().plusDays(30));
        Long proximas = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM cuotas
                WHERE empresa_id = :empresaId
                  AND contrato_id IN (:contratoIds)
                  AND estado IN ('PENDIENTE','PARCIAL')
                  AND fecha_vencimiento BETWEEN :hoy AND :limite
                """, parametros, Long.class);
        Long pagosPendientes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pagos
                WHERE empresa_id = :empresaId
                  AND contrato_id IN (:contratoIds)
                  AND estado IN ('PENDIENTE','EN_REVISION')
                """, parametros, Long.class);
        BigDecimal recaudado = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(valor), 0)
                FROM pagos
                WHERE empresa_id = :empresaId
                  AND contrato_id IN (:contratoIds)
                  AND estado = 'CONFIRMADO'
                """, parametros, BigDecimal.class);
        BigDecimal porcentaje = saldo.signum() == 0
                ? BigDecimal.ZERO
                : vencida.multiply(BigDecimal.valueOf(100))
                        .divide(saldo, 2, RoundingMode.HALF_UP);
        long planes = contratos.stream().filter(item -> item.planPagoId() != null).count();
        return new CarteraResumenResponse(
                contratos.size(),
                planes,
                contratos.size() - planes,
                totalProgramado,
                dinero(recaudado == null ? totalPagadoCuotas : recaudado),
                saldo,
                vencida,
                dinero(porcentaje),
                proximas == null ? 0 : proximas,
                pagosPendientes == null ? 0 : pagosPendientes
        );
    }

    @Transactional(readOnly = true)
    public List<MetodoPagoResponse> metodosPago(ContextoUsuario contexto) {
        return metodoPagoRepository.findDisponibles(contexto.empresaId()).stream()
                .map(MetodoPagoResponse::from)
                .toList();
    }

    private static BigDecimal suma(List<BigDecimal> valores) {
        return dinero(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate fecha(Date fecha) {
        return fecha == null ? null : fecha.toLocalDate();
    }
}
