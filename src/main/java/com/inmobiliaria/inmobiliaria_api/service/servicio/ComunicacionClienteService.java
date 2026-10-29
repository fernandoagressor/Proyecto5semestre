package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.ComunicacionDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ComunicacionClienteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comunicaciones a clientes. Desde la rama 10 viven en la colección {@code comunicaciones_cliente} de MongoDB. */
@Service
public class ComunicacionClienteService {
    static final String SECUENCIA = "comunicaciones_cliente";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ComunicacionClienteRepository repository;
    private final ServicioClienteAlcanceService alcanceService;
    private final SecuenciaService secuencias;

    public ComunicacionClienteService(ComunicacionClienteRepository repository,
                                      ServicioClienteAlcanceService alcanceService,
                                      SecuenciaService secuencias) {
        this.repository = repository;
        this.alcanceService = alcanceService;
        this.secuencias = secuencias;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComunicacionDtos.Response> listar(ContextoUsuario contexto,
                                                            Long clienteId, String estado,
                                                            int pagina, int tamano) {
        exigirEmpresaCompleta(contexto);
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (clienteId != null) condiciones.add(where("clienteId").is(clienteId));
        if (estado != null && !estado.isBlank()) condiciones.add(where("estado").is(estado.toUpperCase(Locale.ROOT)));
        var resultado = repository.buscar(new Criteria().andOperator(condiciones), PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PaginaResponse.from(resultado, ComunicacionDtos.Response::from);
    }

    @Transactional
    public ComunicacionDtos.Response crear(ContextoUsuario contexto,
                                            ComunicacionDtos.CrearRequest request,
                                            boolean masiva) {
        exigirEmpresaCompleta(contexto);
        if (!masiva) alcanceService.validarCliente(contexto, request.clienteId());
        String canal = requerido(request.canal()).toUpperCase(Locale.ROOT);
        if (!List.of("EMAIL", "SMS", "WHATSAPP", "PUSH", "TELEFONO").contains(canal)) {
            throw new ReglaNegocioException("El canal no es válido");
        }
        if (request.programadaPara() != null && !request.programadaPara().isAfter(LocalDateTime.now(BOGOTA))) {
            throw new ReglaNegocioException("La programación debe estar en el futuro");
        }
        ComunicacionCliente comunicacion = new ComunicacionCliente(contexto.empresaId(),
                masiva ? null : request.clienteId(), contexto.usuarioId(), canal,
                normalizar(request.asunto()), requerido(request.contenido()), masiva,
                request.programadaPara());
        comunicacion.identificar(secuencias.siguiente(SECUENCIA));
        return ComunicacionDtos.Response.from(repository.insertar(comunicacion));
    }

    @Transactional
    public ComunicacionDtos.Response confirmarEnvio(ContextoUsuario contexto, Long id) {
        exigirEmpresaCompleta(contexto);
        ComunicacionCliente c = obtener(contexto, id);
        c.confirmarEnvio(LocalDateTime.now(BOGOTA));
        return ComunicacionDtos.Response.from(guardar(c));
    }

    @Transactional
    public ComunicacionDtos.Response marcarFallida(ContextoUsuario contexto, Long id) {
        exigirEmpresaCompleta(contexto);
        ComunicacionCliente c = obtener(contexto, id);
        c.marcarFallida();
        return ComunicacionDtos.Response.from(guardar(c));
    }

    private ComunicacionCliente guardar(ComunicacionCliente comunicacion) {
        try {
            return repository.guardar(comunicacion);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictoException("La comunicación fue modificada por otro usuario");
        }
    }

    private ComunicacionCliente obtener(ContextoUsuario contexto, Long id) {
        return repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La comunicación no existe"));
    }

    private void exigirEmpresaCompleta(ContextoUsuario contexto) {
        if (!alcanceService.alcanceInterno(contexto).empresaCompleta()) {
            throw new com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException(
                    "Las comunicaciones requieren alcance completo sobre la empresa");
        }
    }

    private static String requerido(String valor) {
        String n = normalizar(valor);
        if (n == null) throw new ReglaNegocioException("El valor es obligatorio");
        return n;
    }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
