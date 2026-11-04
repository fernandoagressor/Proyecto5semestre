package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.inventario.AvanceObraDtos;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.AvanceObra;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.AvanceObraRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvanceObraServiceTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(9L, 3L);
    private static final AvanceObraDtos.RegistroRequest SOLICITUD = new AvanceObraDtos.RegistroRequest(
            LocalDate.of(2026, 11, 3), new BigDecimal("20"), "Movimiento de tierras terminado");

    private final AvanceObraRepository avances = mock(AvanceObraRepository.class);
    private final ProyectoRepository proyectos = mock(ProyectoRepository.class);
    private final AlcanceProyectoService alcances = mock(AlcanceProyectoService.class);
    private final AvanceObraService service = new AvanceObraService(avances, proyectos, alcances);

    @Test
    void registraElAvanceEnUnProyectoDeLaEmpresa() {
        Proyecto proyecto = new Proyecto(3L, "PRY-1", "Altos del Río", null, "Neiva", "Huila",
                null, null, null, null, null, null);
        when(proyectos.findForUpdate(77L, 3L)).thenReturn(Optional.of(proyecto));
        when(avances.save(any(AvanceObra.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        AvanceObraDtos.Response respuesta = service.registrar(SESION, 77L, SOLICITUD);

        assertThat(respuesta.porcentaje()).isEqualByComparingTo("20");
        assertThat(respuesta.registradoPor()).isEqualTo(9L);
        assertThat(proyecto.getPorcentajeAvance()).isEqualByComparingTo("20");
        verify(alcances).verificarProyecto(9L, 3L, proyecto);
    }

    @Test
    void unProyectoDeOtraEmpresaNoExiste() {
        when(proyectos.findForUpdate(77L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(SESION, 77L, SOLICITUD))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(avances, never()).save(any());
    }
}
