package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.inventario.AvanceObraDtos;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.AvanceObra;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.AvanceObraRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso: registrar y consultar los avances de obra de un proyecto. */
@Service
public class AvanceObraService {

    private final AvanceObraRepository avanceObraRepository;
    private final ProyectoRepository proyectoRepository;
    private final AlcanceProyectoService alcanceProyectoService;

    public AvanceObraService(
            AvanceObraRepository avanceObraRepository,
            ProyectoRepository proyectoRepository,
            AlcanceProyectoService alcanceProyectoService
    ) {
        this.avanceObraRepository = avanceObraRepository;
        this.proyectoRepository = proyectoRepository;
        this.alcanceProyectoService = alcanceProyectoService;
    }

    @Transactional(readOnly = true)
    public List<AvanceObraDtos.Response> listar(ContextoUsuario contexto, Long proyectoId) {
        Proyecto proyecto = proyectoRepository
                .findByIdAndEmpresaId(proyectoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El proyecto no existe"));
        alcanceProyectoService.verificarProyecto(
                contexto.usuarioId(), contexto.empresaId(), proyecto);
        return avanceObraRepository
                .findByProyectoIdAndEmpresaIdOrderByFechaDescIdDesc(proyectoId, contexto.empresaId())
                .stream()
                .map(AvanceObraDtos.Response::from)
                .toList();
    }

    @Transactional
    public AvanceObraDtos.Response registrar(
            ContextoUsuario contexto,
            Long proyectoId,
            AvanceObraDtos.RegistroRequest request
    ) {
        Proyecto proyecto = proyectoRepository
                .findForUpdate(proyectoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El proyecto no existe"));
        alcanceProyectoService.verificarProyecto(
                contexto.usuarioId(), contexto.empresaId(), proyecto);
        AvanceObra avance = new AvanceObra(
                proyecto,
                request.fecha(),
                request.porcentaje(),
                request.descripcion(),
                contexto.usuarioId()
        );
        return AvanceObraDtos.Response.from(avanceObraRepository.save(avance));
    }
}
