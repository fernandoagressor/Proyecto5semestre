package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProyectoTest {

    @Test
    void iniciaEnPlaneacionYCeroPorCiento() {
        Proyecto proyecto = proyecto(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 8, 1)
        );

        assertThat(proyecto.getEstado()).isEqualTo(EstadoProyecto.PLANEACION);
        assertThat(proyecto.getPorcentajeAvance()).isZero();
    }

    @Test
    void rechazaFechaFinalAnteriorAlInicio() {
        assertThatThrownBy(() -> proyecto(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1)
        )).isInstanceOf(ReglaNegocioException.class);
    }

    private Proyecto proyecto(LocalDate inicio, LocalDate fin) {
        return new Proyecto(
                1L,
                "PR-01",
                "Proyecto Norte",
                null,
                "Bogotá",
                "Cundinamarca",
                null,
                inicio,
                fin,
                100,
                null,
                null
        );
    }
}
