package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.GenerarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EtapaProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Manzana;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.EtapaProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteHistorialEstadoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LotePrecioHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ManzanaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Etapa con N manzanas y manzana con N lotes, con códigos automáticos. */
class InventarioServiceGeneracionTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(9L, 3L);

    private final ProyectoService proyectos = mock(ProyectoService.class);
    private final EtapaProyectoRepository etapas = mock(EtapaProyectoRepository.class);
    private final ManzanaRepository manzanas = mock(ManzanaRepository.class);
    private final LoteRepository lotes = mock(LoteRepository.class);
    private final InventarioAvisos avisos = mock(InventarioAvisos.class);
    private final InventarioService service = new InventarioService(proyectos, etapas, manzanas, lotes,
            mock(LotePrecioHistorialRepository.class), mock(LoteHistorialEstadoRepository.class), avisos);
    private final Proyecto proyecto = new Proyecto(3L, "PRY-1", "Altos del Río", null, "Neiva", "Huila",
            null, null, null, null, null, null);

    @BeforeEach
    void preparar() {
        when(proyectos.obtenerAutorizado(SESION, 7L)).thenReturn(proyecto);
        when(etapas.save(any(EtapaProyecto.class))).thenAnswer(i -> i.getArgument(0));
        when(manzanas.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(lotes.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void creaLaEtapaConCodigoAutomaticoYSusManzanas() {
        when(etapas.maxSecuencia(7L)).thenReturn(1);
        when(etapas.existsByProyectoIdAndCodigoIgnoreCase(7L, "E02")).thenReturn(false);

        var etapa = service.crearEtapa(SESION, 7L,
                new EtapaRequest(null, "Etapa 2", null, null, null, null, 10));

        assertThat(etapa.codigo()).isEqualTo("E02");
        assertThat(etapa.secuencia()).isEqualTo(2);
        ArgumentCaptor<List<Manzana>> creadas = ArgumentCaptor.forClass(List.class);
        verify(manzanas).saveAll(creadas.capture());
        assertThat(creadas.getValue()).hasSize(10);
        assertThat(creadas.getValue().get(0).getCodigo()).isEqualTo("M01");
        assertThat(creadas.getValue().get(0).getNombre()).isEqualTo("Manzana 1");
        assertThat(creadas.getValue().get(9).getCodigo()).isEqualTo("M10");
        verify(avisos).registrar(eq(SESION), eq(proyecto), eq("ETAPA_CREADA"), eq("ETAPA"), any(),
                anyString(), anyString(), any(), any());
    }

    @Test
    void generaLosLotesDespuesDelUltimoNumeroYPendientes() {
        prepararManzana();
        when(lotes.numerosDeManzana(any())).thenReturn(List.of("1", "2"));

        List<LoteResponse> creados = service.generarLotes(SESION, 7L, 11L, 21L, new GenerarRequest(3));

        assertThat(creados).extracting(LoteResponse::codigo)
                .containsExactly("E01-M03-L03", "E01-M03-L04", "E01-M03-L05");
        assertThat(creados).extracting(LoteResponse::numero).containsExactly("3", "4", "5");
        assertThat(creados).extracting(LoteResponse::estado).containsOnly(EstadoLote.PENDIENTE);
        verify(avisos).registrar(eq(SESION), eq(proyecto), eq("LOTES_GENERADOS"), eq("MANZANA"), any(),
                anyString(), anyString(), any(), any());
    }

    @Test
    void respetaElMaximoDeLotesDelProyecto() {
        prepararManzana();
        Proyecto limitado = new Proyecto(3L, "PRY-2", "Limitado", null, "Neiva", "Huila",
                null, null, null, 10, null, null);
        when(proyectos.obtenerAutorizado(SESION, 7L)).thenReturn(limitado);
        when(lotes.countByProyectoIdAndEmpresaId(7L, 3L)).thenReturn(8L);

        assertThatThrownBy(() -> service.generarLotes(SESION, 7L, 11L, 21L, new GenerarRequest(5)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("puedes crear 2 más");
        verify(lotes, never()).saveAll(anyList());
    }

    private void prepararManzana() {
        EtapaProyecto etapa = new EtapaProyecto(3L, 7L, "E01", "Etapa 1", 1, null, null);
        Manzana manzana = new Manzana(3L, 7L, 11L, "M03", "Manzana 3", 3);
        when(etapas.findByIdAndEmpresaIdAndProyectoId(11L, 3L, 7L)).thenReturn(Optional.of(etapa));
        when(manzanas.findByIdAndEmpresaIdAndProyectoIdAndEtapaId(21L, 3L, 7L, 11L))
                .thenReturn(Optional.of(manzana));
        when(lotes.codigosConPrefijo(anyLong(), anyString())).thenReturn(List.of());
    }
}
