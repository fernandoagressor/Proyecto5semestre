package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.EstadoUsuario;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final EmpresaRepository empresaRepository;
    private final LoteRepository loteRepository;
    private final AlcanceProyectoService alcanceProyectoService;
    private final UsuarioRepository usuarioRepository;

    public ProyectoService(
            ProyectoRepository proyectoRepository,
            EmpresaRepository empresaRepository,
            LoteRepository loteRepository,
            AlcanceProyectoService alcanceProyectoService,
            UsuarioRepository usuarioRepository
    ) {
        this.proyectoRepository = proyectoRepository;
        this.empresaRepository = empresaRepository;
        this.loteRepository = loteRepository;
        this.alcanceProyectoService = alcanceProyectoService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProyectoResponse> listar(
            ContextoUsuario contexto,
            String texto,
            EstadoProyecto estado,
            int pagina,
            int tamano
    ) {
        AlcanceProyectos alcance = alcanceProyectoService.obtener(
                contexto.usuarioId(),
                contexto.empresaId()
        );
        Specification<Proyecto> filtro = Specification.where(
                (root, query, cb) ->
                        cb.equal(root.get("empresaId"), contexto.empresaId())
        );

        if (!alcance.empresaCompleta()) {
            filtro = filtro.and((root, query, cb) -> {
                List<Predicate> autorizados = new ArrayList<>();
                if (!alcance.proyectos().isEmpty()) {
                    autorizados.add(root.get("id").in(alcance.proyectos()));
                }
                if (alcance.registrosPropios()) {
                    autorizados.add(
                            cb.equal(root.get("gerenteId"), contexto.usuarioId())
                    );
                }
                return autorizados.isEmpty()
                        ? cb.disjunction()
                        : cb.or(autorizados.toArray(Predicate[]::new));
            });
        }
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        String busqueda = normalizarOpcional(texto);
        if (busqueda != null) {
            String patron = "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("codigo")), patron),
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(cb.lower(root.get("ciudad")), patron)
            ));
        }

        Page<Proyecto> resultado = proyectoRepository.findAll(
                filtro,
                PageRequest.of(
                        paginaValida(pagina),
                        tamanoValido(tamano),
                        Sort.by(Sort.Direction.ASC, "nombre")
                )
        );
        return PaginaResponse.from(resultado, ProyectoResponse::from);
    }

    @Transactional(readOnly = true)
    public ProyectoResponse obtener(ContextoUsuario contexto, Long proyectoId) {
        return ProyectoResponse.from(obtenerAutorizado(contexto, proyectoId));
    }

    @Transactional
    public ProyectoResponse crear(
            ContextoUsuario contexto,
            ProyectoRequest request
    ) {
        alcanceProyectoService.verificarEmpresa(
                contexto.usuarioId(),
                contexto.empresaId()
        );
        Empresa empresa = empresaRepository.findById(contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa autenticada no existe"
                ));
        if (!"ACTIVA".equals(empresa.getEstado())) {
            throw new ReglaNegocioException(
                    "La empresa debe estar activa para crear proyectos"
            );
        }
        if (proyectoRepository.countByEmpresaId(contexto.empresaId())
                >= empresa.getLimiteProyectos()) {
            throw new ReglaNegocioException(
                    "La empresa alcanzó el límite de proyectos de su plan"
            );
        }
        String codigo = codigo(request.codigo());
        validarCodigoDisponible(contexto.empresaId(), codigo, null);
        validarGerente(contexto.empresaId(), request.gerenteId());

        Proyecto proyecto = new Proyecto(
                contexto.empresaId(),
                codigo,
                requerido(request.nombre()),
                normalizarOpcional(request.descripcion()),
                requerido(request.ciudad()),
                requerido(request.departamento()),
                normalizarOpcional(request.direccion()),
                request.fechaInicio(),
                request.fechaFinEstimada(),
                request.maxLotes(),
                normalizarOpcional(request.imagenPrincipalUrl()),
                request.gerenteId()
        );
        return ProyectoResponse.from(proyectoRepository.save(proyecto));
    }

    @Transactional
    public ProyectoResponse actualizar(
            ContextoUsuario contexto,
            Long proyectoId,
            ProyectoRequest request
    ) {
        Proyecto proyecto = obtenerAutorizado(contexto, proyectoId);
        String codigo = codigo(request.codigo());
        validarCodigoDisponible(contexto.empresaId(), codigo, proyectoId);
        validarGerente(contexto.empresaId(), request.gerenteId());

        long lotesActuales = loteRepository.countByProyectoIdAndEmpresaId(
                proyectoId,
                contexto.empresaId()
        );
        if (request.maxLotes() != null && request.maxLotes() < lotesActuales) {
            throw new ReglaNegocioException(
                    "El máximo de lotes no puede ser menor al inventario existente"
            );
        }

        proyecto.actualizar(
                codigo,
                requerido(request.nombre()),
                normalizarOpcional(request.descripcion()),
                requerido(request.ciudad()),
                requerido(request.departamento()),
                normalizarOpcional(request.direccion()),
                request.fechaInicio(),
                request.fechaFinEstimada(),
                request.maxLotes(),
                normalizarOpcional(request.imagenPrincipalUrl()),
                request.gerenteId()
        );
        return ProyectoResponse.from(proyecto);
    }

    @Transactional
    public ProyectoResponse cambiarEstado(
            ContextoUsuario contexto,
            Long proyectoId,
            EstadoProyecto estado
    ) {
        Proyecto proyecto = obtenerAutorizado(contexto, proyectoId);
        proyecto.cambiarEstado(estado);
        return ProyectoResponse.from(proyecto);
    }

    @Transactional(readOnly = true)
    public Proyecto obtenerAutorizado(
            ContextoUsuario contexto,
            Long proyectoId
    ) {
        Proyecto proyecto = proyectoRepository
                .findByIdAndEmpresaId(proyectoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El proyecto no existe"
                ));
        alcanceProyectoService.verificarProyecto(
                contexto.usuarioId(),
                contexto.empresaId(),
                proyecto
        );
        return proyecto;
    }

    private void validarCodigoDisponible(
            Long empresaId,
            String codigo,
            Long proyectoId
    ) {
        boolean existe = proyectoId == null
                ? proyectoRepository.existsByEmpresaIdAndCodigoIgnoreCase(
                        empresaId,
                        codigo
                )
                : proyectoRepository
                        .existsByEmpresaIdAndCodigoIgnoreCaseAndIdNot(
                                empresaId,
                                codigo,
                                proyectoId
                        );
        if (existe) {
            throw new ConflictoException(
                    "Ya existe un proyecto con el código indicado"
            );
        }
    }

    private void validarGerente(Long empresaId, Long gerenteId) {
        if (gerenteId != null && !usuarioRepository
                .existsByIdAndEmpresaIdAndEstado(
                        gerenteId,
                        empresaId,
                        EstadoUsuario.ACTIVO
                )) {
            throw new ReglaNegocioException(
                    "El gerente debe ser un usuario activo de la empresa"
            );
        }
    }

    private String codigo(String valor) {
        return requerido(valor).toUpperCase(Locale.ROOT);
    }

    private String requerido(String valor) {
        return valor.trim();
    }

    private String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private int paginaValida(int pagina) {
        return Math.max(pagina, 0);
    }

    private int tamanoValido(int tamano) {
        return Math.min(Math.max(tamano, 1), 100);
    }
}
