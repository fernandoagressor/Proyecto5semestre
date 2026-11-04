package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvanceObraTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 3);

    @Test
    void registrarUnAvanceActualizaElPorcentajeDelProyecto() {
        Proyecto proyecto = proyecto();

        AvanceObra avance = new AvanceObra(proyecto, HOY, new BigDecimal("35.50"),
                "Vías internas pavimentadas", 9L);

        assertThat(avance.getPorcentaje()).isEqualByComparingTo("35.50");
        assertThat(avance.getEmpresaId()).isEqualTo(3L);
        assertThat(proyecto.getPorcentajeAvance()).isEqualByComparingTo("35.50");
    }

    @Test
    void elAvanceNoPuedeRetroceder() {
        Proyecto proyecto = proyecto();
        new AvanceObra(proyecto, HOY, new BigDecimal("40"), "Redes de acueducto", 9L);

        assertThatThrownBy(() -> new AvanceObra(proyecto, HOY, new BigDecimal("30"), "Error", 9L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no puede ser menor");
        assertThat(proyecto.getPorcentajeAvance()).isEqualByComparingTo("40");
    }

    @Test
    void noAceptaMasDeCienPorCiento() {
        assertThatThrownBy(() -> new AvanceObra(proyecto(), HOY, new BigDecimal("100.01"), "Fin", 9L))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private static Proyecto proyecto() {
        return new Proyecto(3L, "PRY-1", "Altos del Río", null, "Neiva", "Huila",
                null, null, null, null, null, null);
    }
}
