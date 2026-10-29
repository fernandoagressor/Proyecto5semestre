package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.repository.cartera.GestionCobroRepository;
import com.inmobiliaria.inmobiliaria_api.config.RedisCacheConfig;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.DashboardDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbcTemplate;
    private final GerenciaAlcanceService alcanceService;
    private final GestionCobroRepository gestionCobroRepository;

    public DashboardService(JdbcTemplate jdbcTemplate, GerenciaAlcanceService alcanceService,
                            GestionCobroRepository gestionCobroRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.alcanceService = alcanceService;
        this.gestionCobroRepository = gestionCobroRepository;
    }
    @Cacheable(
            cacheNames = RedisCacheConfig.DASHBOARDS,
            key = "'gerencial:' + #contexto.empresaId() + ':' + #contexto.usuarioId()"
                    + " + ':' + #proyectoId + ':' + #desde + ':' + #hasta"
    )
    @Transactional(readOnly = true)
    public DashboardDtos.ResumenGerencial gerencial(ContextoUsuario contexto, Long proyectoId,
                                                      LocalDate desde, LocalDate hasta) {
        PeriodoDashboard periodo = periodo(desde, hasta);
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        BigDecimal ventas = decimal("""
                SELECT COALESCE(SUM(valor_total),0) FROM contratos
                WHERE empresa_id = ? AND estado IN ('VIGENTE','FINALIZADO')
                  AND fecha_firma BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        long contratos = entero("""
                SELECT COUNT(*) FROM contratos
                WHERE empresa_id = ? AND estado IN ('VIGENTE','FINALIZADO')
                  AND fecha_firma BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        BigDecimal recaudo = decimal("""
                SELECT COALESCE(SUM(p.valor),0) FROM pagos p
                LEFT JOIN contratos c ON c.id = p.contrato_id
                WHERE p.empresa_id = ? AND p.estado = 'CONFIRMADO'
                  AND DATE(p.fecha_pago) BETWEEN ? AND ?
                  AND (? IS NULL OR c.proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        Saldo saldo = jdbcTemplate.query("""
                SELECT COALESCE(SUM(q.saldo + q.interes_mora),0) total,
                       COALESCE(SUM(CASE WHEN q.estado = 'VENCIDA'
                              THEN q.saldo + q.interes_mora ELSE 0 END),0) vencido
                FROM cuotas q JOIN contratos c ON c.id = q.contrato_id
                WHERE q.empresa_id = ? AND q.estado NOT IN ('PAGADA','CANCELADA','CONDONADA')
                  AND (? IS NULL OR c.proyecto_id = ?)
                """, rs -> rs.next() ? new Saldo(rs.getBigDecimal("total"), rs.getBigDecimal("vencido"))
                : new Saldo(BigDecimal.ZERO, BigDecimal.ZERO), contexto.empresaId(), proyecto, proyecto);
        BigDecimal costos = decimal("""
                SELECT COALESCE(SUM(valor),0) FROM costos_proyecto
                WHERE empresa_id = ? AND estado IN ('APROBADO','PAGADO')
                  AND fecha_costo BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        Inventario inventario = jdbcTemplate.query("""
                SELECT COUNT(*) cantidad, COALESCE(SUM(valor_lista),0) valor
                FROM lotes WHERE empresa_id = ? AND estado = 'DISPONIBLE'
                  AND (? IS NULL OR proyecto_id = ?)
                """, rs -> rs.next() ? new Inventario(rs.getLong("cantidad"), rs.getBigDecimal("valor"))
                : new Inventario(0, BigDecimal.ZERO), contexto.empresaId(), proyecto, proyecto);
        long oportunidades = entero("""
                SELECT COUNT(*) FROM lectura_oportunidades WHERE empresa_id = ? AND estado = 'ABIERTA'
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        long tareasVencidas = entero("""
                SELECT COUNT(*) FROM lectura_tareas WHERE empresa_id = ? AND estado = 'VENCIDA'
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        long pqrs = entero("""
                SELECT COUNT(*) FROM lectura_solicitudes_pqrs WHERE empresa_id = ?
                  AND estado IN ('ABIERTA','EN_PROCESO','PENDIENTE_CLIENTE','PENDIENTE_TERCERO','REABIERTA')
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        BigDecimal utilidad = ventas.subtract(costos).setScale(2, RoundingMode.HALF_UP);
        return new DashboardDtos.ResumenGerencial(periodo.desde(), periodo.hasta(), proyecto,
                ventas, contratos, recaudo, dinero(saldo.total()), dinero(saldo.vencido()),
                porcentaje(saldo.vencido(), saldo.total()), costos, utilidad,
                porcentaje(utilidad, ventas), inventario.cantidad(), dinero(inventario.valor()),
                oportunidades, tareasVencidas, pqrs);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = RedisCacheConfig.DASHBOARDS,
            key = "'comercial:' + #contexto.empresaId() + ':' + #contexto.usuarioId()"
                    + " + ':' + #proyectoId + ':' + #desde + ':' + #hasta"
    )
    public DashboardDtos.ResumenComercial comercial(ContextoUsuario contexto, Long proyectoId,
                                                      LocalDate desde, LocalDate hasta) {
        PeriodoDashboard periodo = periodo(desde, hasta);
        AlcanceProyectos alcance = alcanceService.obtener(contexto);
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, true);
        Long asesor = !alcance.empresaCompleta() && proyecto == null && alcance.registrosPropios()
                ? contexto.usuarioId() : null;
        long nuevas = entero("""
                SELECT COUNT(*) FROM lectura_oportunidades WHERE empresa_id = ?
                  AND DATE(creado_en) BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto, asesor, asesor);
        long ganadas = entero("""
                SELECT COUNT(*) FROM lectura_oportunidades WHERE empresa_id = ? AND estado = 'GANADA'
                  AND DATE(fecha_cierre) BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto, asesor, asesor);
        long perdidas = entero("""
                SELECT COUNT(*) FROM lectura_oportunidades WHERE empresa_id = ? AND estado = 'PERDIDA'
                  AND DATE(fecha_cierre) BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto, asesor, asesor);
        BigDecimal embudoValor = decimal("""
                SELECT COALESCE(SUM(valor_estimado * probabilidad_cierre / 100),0)
                FROM lectura_oportunidades WHERE empresa_id = ? AND estado = 'ABIERTA'
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                """, contexto.empresaId(), proyecto, proyecto, asesor, asesor);
        long reservas = entero("""
                SELECT COUNT(*) FROM reservas WHERE empresa_id = ? AND estado = 'ACTIVA'
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                """, contexto.empresaId(), proyecto, proyecto, asesor, asesor);
        List<DashboardDtos.Serie> embudo = jdbcTemplate.query("""
                SELECT etapa_actual categoria, COALESCE(SUM(valor_estimado),0) valor, COUNT(*) cantidad
                FROM lectura_oportunidades WHERE empresa_id = ? AND estado = 'ABIERTA'
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                GROUP BY etapa_actual ORDER BY MIN(id)
                """, (rs, row) -> new DashboardDtos.Serie(rs.getString("categoria"),
                dinero(rs.getBigDecimal("valor")), rs.getLong("cantidad")),
                contexto.empresaId(), proyecto, proyecto, asesor, asesor);
        List<DashboardDtos.Serie> ventasAsesor = jdbcTemplate.query("""
                SELECT COALESCE(CAST(asesor_id AS CHAR),'SIN_ASESOR') categoria,
                       COALESCE(SUM(valor_total),0) valor, COUNT(*) cantidad
                FROM contratos WHERE empresa_id = ? AND estado IN ('VIGENTE','FINALIZADO')
                  AND fecha_firma BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?) AND (? IS NULL OR asesor_id = ?)
                GROUP BY asesor_id ORDER BY valor DESC
                """, (rs, row) -> new DashboardDtos.Serie(rs.getString("categoria"),
                dinero(rs.getBigDecimal("valor")), rs.getLong("cantidad")),
                contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto, asesor, asesor);
        return new DashboardDtos.ResumenComercial(periodo.desde(), periodo.hasta(), proyecto,
                nuevas, ganadas, perdidas, embudoValor,
                porcentaje(BigDecimal.valueOf(ganadas), BigDecimal.valueOf(ganadas + perdidas)),
                reservas, embudo, ventasAsesor);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = RedisCacheConfig.DASHBOARDS,
            key = "'cartera:' + #contexto.empresaId() + ':' + #contexto.usuarioId()"
                    + " + ':' + #proyectoId + ':' + #corte"
    )
    public DashboardDtos.ResumenCartera cartera(ContextoUsuario contexto, Long proyectoId,
                                                 LocalDate corte) {
        LocalDate fechaCorte = corte == null ? LocalDate.now(BOGOTA) : corte;
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        Saldo saldo = jdbcTemplate.query("""
                SELECT COALESCE(SUM(q.saldo + q.interes_mora),0) total,
                       COALESCE(SUM(CASE WHEN q.fecha_vencimiento < ?
                              THEN q.saldo + q.interes_mora ELSE 0 END),0) vencido
                FROM cuotas q JOIN contratos c ON c.id = q.contrato_id
                WHERE q.empresa_id = ? AND q.estado NOT IN ('PAGADA','CANCELADA','CONDONADA')
                  AND (? IS NULL OR c.proyecto_id = ?)
                """, rs -> rs.next() ? new Saldo(rs.getBigDecimal("total"), rs.getBigDecimal("vencido"))
                : new Saldo(BigDecimal.ZERO, BigDecimal.ZERO), fechaCorte, contexto.empresaId(), proyecto, proyecto);
        YearMonth mes = YearMonth.from(fechaCorte);
        BigDecimal recaudo = decimal("""
                SELECT COALESCE(SUM(p.valor),0) FROM pagos p
                LEFT JOIN contratos c ON c.id = p.contrato_id
                WHERE p.empresa_id = ? AND p.estado = 'CONFIRMADO'
                  AND DATE(p.fecha_pago) BETWEEN ? AND ? AND (? IS NULL OR c.proyecto_id = ?)
                """, contexto.empresaId(), mes.atDay(1), mes.atEndOfMonth(), proyecto, proyecto);
        long cuotasVencidas = entero("""
                SELECT COUNT(*) FROM cuotas q JOIN contratos c ON c.id = q.contrato_id
                WHERE q.empresa_id = ? AND q.saldo > 0 AND q.fecha_vencimiento < ?
                  AND (? IS NULL OR c.proyecto_id = ?)
                """, contexto.empresaId(), fechaCorte, proyecto, proyecto);
        // Las gestiones de cobro viven en MongoDB (rama 9) con copia del proyecto del contrato.
        long promesas = gestionCobroRepository.contarPromesasDesde(contexto.empresaId(), fechaCorte, proyecto);
        List<DashboardDtos.Serie> edades = jdbcTemplate.query("""
                SELECT CASE WHEN DATEDIFF(?, q.fecha_vencimiento) BETWEEN 1 AND 30 THEN '1-30'
                            WHEN DATEDIFF(?, q.fecha_vencimiento) BETWEEN 31 AND 60 THEN '31-60'
                            WHEN DATEDIFF(?, q.fecha_vencimiento) BETWEEN 61 AND 90 THEN '61-90'
                            ELSE 'MAS_90' END categoria,
                       COALESCE(SUM(q.saldo + q.interes_mora),0) valor, COUNT(*) cantidad
                FROM cuotas q JOIN contratos c ON c.id = q.contrato_id
                WHERE q.empresa_id = ? AND q.saldo + q.interes_mora > 0
                  AND q.fecha_vencimiento < ? AND (? IS NULL OR c.proyecto_id = ?)
                GROUP BY categoria ORDER BY MIN(q.fecha_vencimiento) DESC
                """, (rs, row) -> new DashboardDtos.Serie(rs.getString("categoria"),
                dinero(rs.getBigDecimal("valor")), rs.getLong("cantidad")),
                fechaCorte, fechaCorte, fechaCorte, contexto.empresaId(), fechaCorte, proyecto, proyecto);
        return new DashboardDtos.ResumenCartera(fechaCorte, proyecto, dinero(saldo.total()),
                dinero(saldo.vencido()), recaudo, porcentaje(saldo.vencido(), saldo.total()),
                cuotasVencidas, promesas, edades);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = RedisCacheConfig.DASHBOARDS,
            key = "'servicio:' + #contexto.empresaId() + ':' + #contexto.usuarioId()"
                    + " + ':' + #proyectoId + ':' + #desde + ':' + #hasta"
    )
    public DashboardDtos.ResumenServicio servicio(ContextoUsuario contexto, Long proyectoId,
                                                   LocalDate desde, LocalDate hasta) {
        PeriodoDashboard periodo = periodo(desde, hasta);
        Long proyecto = alcanceService.proyectoParaConsulta(contexto, proyectoId, false);
        long abiertas = entero("""
                SELECT COUNT(*) FROM lectura_solicitudes_pqrs WHERE empresa_id = ?
                  AND estado IN ('ABIERTA','EN_PROCESO','PENDIENTE_CLIENTE','PENDIENTE_TERCERO','REABIERTA')
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        long vencidas = entero("""
                SELECT COUNT(*) FROM lectura_solicitudes_pqrs WHERE empresa_id = ?
                  AND fecha_limite < CURRENT_TIMESTAMP(6)
                  AND estado NOT IN ('RESUELTA','CERRADA','CANCELADA')
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        BigDecimal horas = decimal("""
                SELECT COALESCE(AVG(TIMESTAMPDIFF(MINUTE, creado_en, fecha_primera_respuesta))/60,0)
                FROM lectura_solicitudes_pqrs WHERE empresa_id = ? AND fecha_primera_respuesta IS NOT NULL
                  AND DATE(creado_en) BETWEEN ? AND ? AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        BigDecimal satisfaccion = decimal("""
                SELECT COALESCE(AVG(e.puntuacion),0) FROM lectura_encuestas_satisfaccion e
                WHERE e.empresa_id = ? AND DATE(e.respondida_en) BETWEEN ? AND ?
                  AND (? IS NULL OR e.proyecto_id = ?)
                """, contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        long citas = entero("""
                SELECT COUNT(*) FROM citas_servicio WHERE empresa_id = ?
                  AND estado IN ('PROGRAMADA','CONFIRMADA') AND fecha_inicio >= CURRENT_TIMESTAMP(6)
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        long tickets = entero("""
                SELECT COUNT(*) FROM lectura_tickets_soporte WHERE empresa_id = ?
                  AND estado IN ('ABIERTO','EN_PROCESO','EN_ESPERA','REABIERTO')
                  AND (? IS NULL OR proyecto_id = ?)
                """, contexto.empresaId(), proyecto, proyecto);
        List<DashboardDtos.Serie> tipos = jdbcTemplate.query("""
                SELECT tipo categoria, CAST(COUNT(*) AS DECIMAL(19,2)) valor, COUNT(*) cantidad
                FROM lectura_solicitudes_pqrs WHERE empresa_id = ? AND DATE(creado_en) BETWEEN ? AND ?
                  AND (? IS NULL OR proyecto_id = ?)
                GROUP BY tipo ORDER BY cantidad DESC
                """, (rs, row) -> new DashboardDtos.Serie(rs.getString("categoria"),
                dinero(rs.getBigDecimal("valor")), rs.getLong("cantidad")),
                contexto.empresaId(), periodo.desde(), periodo.hasta(), proyecto, proyecto);
        return new DashboardDtos.ResumenServicio(periodo.desde(), periodo.hasta(), proyecto,
                abiertas, vencidas, horas, satisfaccion, citas, tickets, tipos);
    }

    private PeriodoDashboard periodo(LocalDate desde, LocalDate hasta) {
        return PeriodoDashboard.resolver(desde, hasta, LocalDate.now(BOGOTA));
    }

    private BigDecimal decimal(String sql, Object... parametros) {
        return dinero(jdbcTemplate.queryForObject(sql, BigDecimal.class, parametros));
    }

    private long entero(String sql, Object... parametros) {
        Long valor = jdbcTemplate.queryForObject(sql, Long.class, parametros);
        return valor == null ? 0 : valor;
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private record Saldo(BigDecimal total, BigDecimal vencido) { }
    private record Inventario(long cantidad, BigDecimal valor) { }
}
