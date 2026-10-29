package com.inmobiliaria.inmobiliaria_api.config;

import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.AlmacenTokenReanudacion;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.DespachadorCambios;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.OyenteProyeccion;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.ProyectorLectura;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma el despachador de la sincronización MongoDB → MySQL con todos los proyectores registrados.
 * El observador del Change Stream solo arranca con {@code app.sync.enabled=true}.
 */
@Configuration
public class SincronizacionConfig {

    @Bean
    public DespachadorCambios despachadorCambios(ObjectProvider<ProyectorLectura> proyectores,
                                                 AlmacenTokenReanudacion tokens,
                                                 ObjectProvider<OyenteProyeccion> oyentes) {
        return new DespachadorCambios(proyectores.orderedStream().toList(), tokens,
                oyentes.orderedStream().toList());
    }
}
