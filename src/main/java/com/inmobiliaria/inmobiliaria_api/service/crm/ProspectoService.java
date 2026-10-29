package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoCalificacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ProspectoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prospectos comerciales. Desde la rama 8 viven en la colección {@code prospectos} de MongoDB;
 * el alcance (proyectos y asesores) se sigue validando contra MySQL.
 */
@Service
public class ProspectoService {

    private final ProspectoRepository prospectoRepository;
    private final CrmAlcanceService crmAlcanceService;
    private final SecuenciaService secuencias;

    public ProspectoService(
            ProspectoRepository prospectoRepository,
            CrmAlcanceService crmAlcanceService,
            SecuenciaService secuencias
    ) {
        this.prospectoRepository = prospectoRepository;
        this.crmAlcanceService = crmAlcanceService;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProspectoResponse> listar(
            ContextoUsuario contexto,
            String texto,
            EstadoProspecto estado,
            TemperaturaProspecto temperatura,
            Long proyectoId,
            Long asesorId,
            int pagina,
            int tamano
    ) {
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(crmAlcanceService.filtroMongo(contexto));
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (temperatura != null) condiciones.add(where("temperatura").is(temperatura.name()));
        if (proyectoId != null) condiciones.add(where("proyectoId").is(proyectoId));
        if (asesorId != null) condiciones.add(where("asesorId").is(asesorId));
        String busqueda = normalizar(texto);
        if (busqueda != null) {
            String patron = Pattern.quote(busqueda);
            condiciones.add(new Criteria().orOperator(
                    where("nombres").regex(patron, "i"),
                    where("correo").regex(patron, "i"),
                    where("telefono").regex(patron)
            ));
        }
        var resultado = prospectoRepository.buscar(
                new Criteria().andOperator(condiciones),
                PageRequest.of(
                        paginaValida(pagina),
                        tamanoValido(tamano),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))
                )
        );
        return PaginaResponse.from(resultado, ProspectoResponse::from);
    }

    @Transactional(readOnly = true)
    public ProspectoResponse obtener(
            ContextoUsuario contexto,
            Long prospectoId
    ) {
        return ProspectoResponse.from(obtenerAutorizado(contexto, prospectoId));
    }

    @Transactional
    public ProspectoResponse crear(
            ContextoUsuario contexto,
            ProspectoRequest request
    ) {
        validarContacto(request.telefono(), request.correo());
        Long asesorId = crmAlcanceService.validarAsesor(
                contexto,
                request.asesorId()
        );
        crmAlcanceService.verificarRegistro(
                contexto,
                request.proyectoId(),
                asesorId
        );
        validarDuplicado(
                contexto.empresaId(),
                request.correo(),
                request.telefono(),
                null
        );

        Prospecto prospecto = new Prospecto(
                contexto.empresaId(),
                request.proyectoId(),
                asesorId,
                requerido(request.nombres()),
                normalizar(request.telefono()),
                correo(request.correo()),
                request.fuente(),
                request.presupuesto(),
                request.temperatura(),
                normalizar(request.observaciones())
        );
        prospecto.identificar(secuencias.siguiente(SecuenciasCrm.PROSPECTOS));
        return ProspectoResponse.from(prospectoRepository.insertar(prospecto));
    }

    @Transactional
    public ProspectoResponse actualizar(
            ContextoUsuario contexto,
            Long prospectoId,
            ProspectoRequest request
    ) {
        Prospecto prospecto = obtenerAutorizado(contexto, prospectoId);
        if (request.asesorId() != null
                && !request.asesorId().equals(prospecto.getAsesorId())) {
            throw new ReglaNegocioException(
                    "Use la operación de reasignación para cambiar el asesor"
            );
        }
        validarContacto(request.telefono(), request.correo());
        crmAlcanceService.verificarRegistro(
                contexto,
                request.proyectoId(),
                prospecto.getAsesorId()
        );
        validarDuplicado(
                contexto.empresaId(),
                request.correo(),
                request.telefono(),
                prospectoId
        );
        prospecto.actualizarDatos(
                request.proyectoId(),
                requerido(request.nombres()),
                normalizar(request.telefono()),
                correo(request.correo()),
                request.fuente(),
                request.presupuesto(),
                request.temperatura(),
                normalizar(request.observaciones())
        );
        return ProspectoResponse.from(guardar(prospecto));
    }

    @Transactional
    public ProspectoResponse calificar(
            ContextoUsuario contexto,
            Long prospectoId,
            ProspectoCalificacionRequest request
    ) {
        Prospecto prospecto = obtenerAutorizado(contexto, prospectoId);
        prospecto.cambiarCalificacion(
                request.estado(),
                request.temperatura(),
                normalizar(request.justificacion())
        );
        return ProspectoResponse.from(guardar(prospecto));
    }

    @Transactional
    public ProspectoResponse reasignar(
            ContextoUsuario contexto,
            Long prospectoId,
            Long asesorId
    ) {
        Prospecto prospecto = obtenerAutorizado(contexto, prospectoId);
        crmAlcanceService.verificarAlcanceAsesorDestino(
                contexto,
                prospecto.getProyectoId(),
                asesorId
        );
        prospecto.reasignar(asesorId);
        return ProspectoResponse.from(guardar(prospecto));
    }

    @Transactional(readOnly = true)
    public Prospecto obtenerAutorizado(
            ContextoUsuario contexto,
            Long prospectoId
    ) {
        Prospecto prospecto = prospectoRepository
                .findByIdAndEmpresaId(prospectoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El prospecto no existe"
                ));
        crmAlcanceService.verificarRegistro(
                contexto,
                prospecto.getProyectoId(),
                prospecto.getAsesorId()
        );
        return prospecto;
    }

    /** Borra un prospecto recién creado por una operación que luego falló (compensación). */
    public void eliminarPorCompensacion(Long prospectoId) {
        prospectoRepository.eliminar(prospectoId);
    }

    private Prospecto guardar(Prospecto prospecto) {
        try {
            return prospectoRepository.guardar(prospecto);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("El prospecto fue modificado por otro usuario");
        }
    }

    private void validarDuplicado(
            Long empresaId,
            String correo,
            String telefono,
            Long excluirId
    ) {
        String correoNormalizado = correo(correo);
        String telefonoNormalizado = normalizar(telefono);
        if (prospectoRepository.contarContactosActivos(
                empresaId,
                correoNormalizado,
                telefonoNormalizado,
                excluirId
        ) > 0) {
            throw new ConflictoException(
                    "Ya existe un prospecto activo con el mismo correo o teléfono"
            );
        }
    }

    private static void validarContacto(String telefono, String correo) {
        if (normalizar(telefono) == null && normalizar(correo) == null) {
            throw new ReglaNegocioException(
                    "Debe registrar al menos un teléfono o correo"
            );
        }
    }

    private static String correo(String valor) {
        String normalizado = normalizar(valor);
        return normalizado == null
                ? null
                : normalizado.toLowerCase(Locale.ROOT);
    }

    private static String requerido(String valor) {
        String normalizado = normalizar(valor);
        if (normalizado == null) {
            throw new ReglaNegocioException("El nombre es obligatorio");
        }
        return normalizado;
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static int paginaValida(int pagina) {
        return Math.max(pagina, 0);
    }

    private static int tamanoValido(int tamano) {
        return Math.min(Math.max(tamano, 1), 100);
    }
}
