package com.inmobiliaria.inmobiliaria_api.service.cartera;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CarteraMoraJob {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CarteraGestionService carteraGestionService;
    private final JdbcTemplate jdbcTemplate;

    public CarteraMoraJob(CarteraGestionService carteraGestionService, JdbcTemplate jdbcTemplate) {
        this.carteraGestionService = carteraGestionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${app.cartera.mora-cron:0 15 1 * * *}", zone = "America/Bogota")
    public void actualizar() {
        LocalDate fecha = LocalDate.now(BOGOTA).minusDays(1);
        jdbcTemplate.queryForList("SELECT id FROM empresas WHERE estado = 'ACTIVA'", Long.class)
                .forEach(empresaId -> carteraGestionService.actualizarMoraEmpresa(empresaId, fecha));
    }
}
