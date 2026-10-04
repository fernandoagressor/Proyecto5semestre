package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.ConocimientoDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ArticuloConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CategoriaConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseConocimientoService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CategoriaConocimientoRepository categoriaRepository;
    private final ArticuloConocimientoRepository articuloRepository;
    private final ServicioClienteAlcanceService alcanceService;

    public BaseConocimientoService(CategoriaConocimientoRepository categoriaRepository,
                                   ArticuloConocimientoRepository articuloRepository,
                                   ServicioClienteAlcanceService alcanceService) {
        this.categoriaRepository = categoriaRepository;
        this.articuloRepository = articuloRepository;
        this.alcanceService = alcanceService;
    }

    @Transactional(readOnly = true)
    public List<ConocimientoDtos.CategoriaResponse> categorias(ContextoUsuario contexto) {
        return categoriaRepository.listarDisponibles(contexto.empresaId()).stream()
                .map(ConocimientoDtos.CategoriaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ConocimientoDtos.ArticuloResponse> listar(ContextoUsuario contexto,
                                                                    Long categoriaId, String texto,
                                                                    int pagina, int tamano) {
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        Specification<ArticuloConocimiento> filtro = (root, query, cb) -> cb.or(
                cb.isNull(root.get("empresaId")), cb.equal(root.get("empresaId"), contexto.empresaId()));
        if (cliente) filtro = filtro.and((root, query, cb) -> cb.and(
                cb.isTrue(root.get("visibleClientes")), cb.equal(root.get("estado"), "PUBLICADO")));
        if (categoriaId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("categoriaId"), categoriaId));
        if (texto != null && !texto.isBlank()) {
            String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> cb.like(cb.lower(root.get("titulo")), patron));
        }
        Page<ArticuloConocimiento> resultado = articuloRepository.findAll(filtro, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "publicadoEn")));
        return PaginaResponse.from(resultado, ConocimientoDtos.ArticuloResponse::from);
    }

    @Transactional(readOnly = true)
    public ConocimientoDtos.ArticuloResponse obtener(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloRepository.findDisponible(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El artículo no existe"));
        if (alcanceService.clienteAutenticado(contexto).isPresent()
                && (!a.isVisibleClientes() || !"PUBLICADO".equals(a.getEstado()))) {
            throw new AccesoDenegadoException("El artículo no está publicado en el portal");
        }
        return ConocimientoDtos.ArticuloResponse.from(a);
    }

    @Transactional
    public ConocimientoDtos.CategoriaResponse crearCategoria(ContextoUsuario contexto,
                                                              ConocimientoDtos.CategoriaRequest request) {
        CategoriaConocimiento c = new CategoriaConocimiento(contexto.empresaId(), requerido(request.nombre()),
                normalizar(request.descripcion()), request.orden());
        try { return ConocimientoDtos.CategoriaResponse.from(categoriaRepository.save(c)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictoException("Ya existe una categoría con ese nombre"); }
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse crearArticulo(ContextoUsuario contexto,
                                                            ConocimientoDtos.ArticuloRequest request) {
        validarCategoria(contexto, request.categoriaId());
        ArticuloConocimiento a = new ArticuloConocimiento(contexto.empresaId(), request.categoriaId(),
                requerido(request.titulo()), slug(request.slug(), request.titulo()),
                requerido(request.contenido()), request.visibleClientes(), contexto.usuarioId());
        try { return ConocimientoDtos.ArticuloResponse.from(articuloRepository.save(a)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictoException("Ya existe un artículo con ese identificador"); }
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse actualizar(ContextoUsuario contexto, Long id,
                                                         ConocimientoDtos.ArticuloRequest request) {
        validarCategoria(contexto, request.categoriaId());
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.actualizar(request.categoriaId(), requerido(request.titulo()), slug(request.slug(), request.titulo()),
                requerido(request.contenido()), request.visibleClientes());
        return ConocimientoDtos.ArticuloResponse.from(a);
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse publicar(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.publicar(LocalDateTime.now(BOGOTA));
        return ConocimientoDtos.ArticuloResponse.from(a);
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse archivar(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.archivar();
        return ConocimientoDtos.ArticuloResponse.from(a);
    }

    private ArticuloConocimiento articuloEmpresa(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El artículo no existe"));
        if (!contexto.empresaId().equals(a.getEmpresaId())) {
            throw new AccesoDenegadoException("Los artículos globales o de otra empresa no pueden modificarse");
        }
        return a;
    }

    private void validarCategoria(ContextoUsuario contexto, Long id) {
        categoriaRepository.findDisponible(id, contexto.empresaId())
                .orElseThrow(() -> new ReglaNegocioException("La categoría no existe"));
    }

    private static String slug(String solicitado, String titulo) {
        String base = solicitado == null || solicitado.isBlank() ? titulo : solicitado;
        String sinAcentos = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String resultado = sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (resultado.isBlank()) throw new ReglaNegocioException("No fue posible generar el identificador del artículo");
        return resultado;
    }

    private static String requerido(String valor) { String n = normalizar(valor); if (n == null) throw new ReglaNegocioException("El valor es obligatorio"); return n; }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
