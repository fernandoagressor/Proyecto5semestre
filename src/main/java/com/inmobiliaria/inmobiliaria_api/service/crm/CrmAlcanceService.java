package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.EstadoUsuario;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmAlcanceService {

    private final AlcanceProyectoService alcanceProyectoService;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;

    public CrmAlcanceService(
            AlcanceProyectoService alcanceProyectoService,
            ProyectoRepository proyectoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.alcanceProyectoService = alcanceProyectoService;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public AlcanceProyectos obtener(ContextoUsuario contexto) {
        return alcanceProyectoService.obtener(
                contexto.usuarioId(),
                contexto.empresaId()
        );
    }

    @Transactional(readOnly = true)
    public Proyecto verificarRegistro(
            ContextoUsuario contexto,
            Long proyectoId,
            Long asesorId
    ) {
        Proyecto proyecto = null;
        if (proyectoId != null) {
            proyecto = proyectoRepository
                    .findByIdAndEmpresaId(proyectoId, contexto.empresaId())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El proyecto indicado no existe en la empresa"
                    ));
        }

        AlcanceProyectos alcance = obtener(contexto);
        boolean permitido = alcance.empresaCompleta()
                || (proyectoId != null
                && alcance.proyectos().contains(proyectoId))
                || (alcance.registrosPropios()
                && contexto.usuarioId().equals(asesorId));
        if (!permitido) {
            throw new AccesoDenegadoException(
                    "El usuario no tiene alcance sobre el registro comercial"
            );
        }
        return proyecto;
    }

    @Transactional(readOnly = true)
    public Long validarAsesor(
            ContextoUsuario contexto,
            Long asesorSolicitado
    ) {
        Long asesorId = asesorSolicitado == null
                ? contexto.usuarioId()
                : asesorSolicitado;
        if (!usuarioRepository.existsByIdAndEmpresaIdAndEstado(
                asesorId,
                contexto.empresaId(),
                EstadoUsuario.ACTIVO
        )) {
            throw new ReglaNegocioException(
                    "El asesor debe ser un usuario activo de la empresa"
            );
        }
        return asesorId;
    }

    @Transactional(readOnly = true)
    public void verificarAlcanceAsesorDestino(
            ContextoUsuario contexto,
            Long proyectoId,
            Long asesorId
    ) {
        validarAsesor(contexto, asesorId);
        AlcanceProyectos alcanceDestino = alcanceProyectoService.obtener(
                asesorId,
                contexto.empresaId()
        );
        if (!alcanceDestino.empresaCompleta()
                && !alcanceDestino.registrosPropios()
                && (proyectoId == null
                || !alcanceDestino.proyectos().contains(proyectoId))) {
            throw new ReglaNegocioException(
                    "El asesor de destino no tiene alcance sobre el registro"
            );
        }
    }

    /**
     * Filtro de MongoDB con la empresa y el alcance del usuario sobre registros comerciales que
     * tienen {@code empresaId}, {@code proyectoId} y {@code asesorId}.
     */
    @Transactional(readOnly = true)
    public Criteria filtroMongo(ContextoUsuario contexto) {
        AlcanceProyectos alcance = obtener(contexto);
        var empresa = where("empresaId").is(contexto.empresaId());
        if (alcance.empresaCompleta()) {
            return empresa;
        }
        List<Criteria> permitidos = new ArrayList<>();
        if (!alcance.proyectos().isEmpty()) {
            permitidos.add(where("proyectoId").in(alcance.proyectos()));
        }
        if (alcance.registrosPropios()) {
            permitidos.add(where("asesorId").is(contexto.usuarioId()));
        }
        var alcanceRegistro = permitidos.isEmpty()
                ? where("id").in(List.of())
                : new Criteria().orOperator(permitidos);
        return new Criteria().andOperator(empresa, alcanceRegistro);
    }
}
