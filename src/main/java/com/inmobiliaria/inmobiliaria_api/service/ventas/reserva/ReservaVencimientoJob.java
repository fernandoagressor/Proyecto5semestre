package com.inmobiliaria.inmobiliaria_api.service.ventas.reserva;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservaVencimientoJob {

    private final ReservaService reservaService;

    public ReservaVencimientoJob(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.reservas.vencimientos-intervalo-ms:60000}"
    )
    public void ejecutar() {
        reservaService.procesarVencimientos();
    }
}
