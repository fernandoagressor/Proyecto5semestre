package com.inmobiliaria.inmobiliaria_api.service.notificacion;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.notificacion.NotificacionDtos;
import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final NotificacionRepository repository;

    public NotificacionService(NotificacionRepository repository) { this.repository = repository; }

    @Transactional
    public Notificacion crear(Long empresaId, Long usuarioId, String tipo, String titulo,
                              String mensaje, String entidadTipo, Long entidadId, String prioridad) {
        if (usuarioId == null) { return null; }
        return repository.save(new Notificacion(empresaId, usuarioId, tipo, titulo, mensaje,
                entidadTipo, entidadId, prioridad, null));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionDtos.Response> listar(ContextoUsuario contexto,
                                                            Boolean leida, int pagina, int tamano) {
        Specification<Notificacion> filtro = (root, query, cb) -> cb.equal(root.get("usuarioId"), contexto.usuarioId());
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        filtro = filtro.and((root, query, cb) -> cb.or(
                cb.isNull(root.get("expiraEn")), cb.greaterThan(root.get("expiraEn"), ahora)));
        if (leida != null) {
            filtro = filtro.and((root, query, cb) -> leida
                    ? cb.isNotNull(root.get("leidaEn")) : cb.isNull(root.get("leidaEn")));
        }
        Page<Notificacion> resultado = repository.findAll(filtro, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "creadoEn")));
        return PaginaResponse.from(resultado, NotificacionDtos.Response::from);
    }

    @Transactional
    public NotificacionDtos.Response marcarLeida(ContextoUsuario contexto, Long id) {
        Notificacion n = repository.findByIdAndUsuarioId(id, contexto.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La notificación no existe"));
        n.marcarLeida(LocalDateTime.now(BOGOTA));
        return NotificacionDtos.Response.from(n);
    }

    @Transactional(readOnly = true)
    public NotificacionDtos.Resumen resumen(ContextoUsuario contexto) {
        return new NotificacionDtos.Resumen(repository.countVigentesNoLeidas(contexto.usuarioId()));
    }
}
