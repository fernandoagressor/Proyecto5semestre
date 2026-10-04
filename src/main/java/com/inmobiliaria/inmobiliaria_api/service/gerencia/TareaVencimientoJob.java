package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TareaVencimientoJob {
    private final TareaService tareaService;

    public TareaVencimientoJob(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    @Scheduled(cron = "${app.tareas.vencimiento-cron:0 10 0 * * *}", zone = "America/Bogota")
    public void actualizarVencidas() {
        tareaService.procesarVencidas();
    }
}
