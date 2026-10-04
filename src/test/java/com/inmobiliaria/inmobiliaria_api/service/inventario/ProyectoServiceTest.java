package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProyectoServiceTest {

    @Test
    void buscaProyectoDentroDeLaEmpresaAutenticada() {
        ProyectoRepository proyectos = mock(ProyectoRepository.class);
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        LoteRepository lotes = mock(LoteRepository.class);
        AlcanceProyectoService alcances = mock(AlcanceProyectoService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        ProyectoService service = new ProyectoService(
                proyectos,
                empresas,
                lotes,
                alcances,
                usuarios
        );
        when(proyectos.findByIdAndEmpresaId(77L, 3L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.obtener(new ContextoUsuario(8L, 3L), 77L)
        ).isInstanceOf(RecursoNoEncontradoException.class);

        verify(proyectos).findByIdAndEmpresaId(77L, 3L);
    }
}
