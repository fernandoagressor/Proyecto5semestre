package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.ConocimientoDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ArticuloConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CategoriaConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base de conocimiento. Desde la rama 10 categorías y artículos viven en MongoDB
 * ({@code categorias_conocimiento} y {@code articulos_conocimiento}); lo que no tiene empresa es
 * global y lo ven todas.
 */
@Service
public class BaseConocimientoService {
    static final String SECUENCIA_CATEGORIAS = "categorias_conocimiento";
    static final String SECUENCIA_ARTICULOS = "articulos_conocimiento";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CategoriaConocimientoRepository categoriaRepository;
    private final ArticuloConocimientoRepository articuloRepository;
    private final ServicioClienteAlcanceService alcanceService;
    private final SecuenciaService secuencias;

    public BaseConocimientoService(CategoriaConocimientoRepository categoriaRepository,
                                   ArticuloConocimientoRepository articuloRepository,
                                   ServicioClienteAlcanceService alcanceService,
                                   SecuenciaService secuencias) {
        this.categoriaRepository = categoriaRepository;
        this.articuloRepository = articuloRepository;
        this.alcanceService = alcanceService;
        this.secuencias = secuencias;
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
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(ArticuloConocimientoRepository.disponiblePara(contexto.empresaId()));
        if (cliente) condiciones.add(where("visibleClientes").is(true).and("estado").is("PUBLICADO"));
        if (categoriaId != null) condiciones.add(where("categoriaId").is(categoriaId));
        if (texto != null && !texto.isBlank()) {
            // Igual que el LIKE '%texto%' anterior: sin distinguir mayúsculas y sin interpretar el texto como regex.
            condiciones.add(where("titulo").regex(Pattern.quote(texto.trim()), "i"));
        }
        var resultado = articuloRepository.buscar(new Criteria().andOperator(condiciones), PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "publicadoEn").and(Sort.by(Sort.Direction.DESC, "id"))));
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
        c.identificar(secuencias.siguiente(SECUENCIA_CATEGORIAS));
        try { return ConocimientoDtos.CategoriaResponse.from(categoriaRepository.insertar(c)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictoException("Ya existe una categoría con ese nombre"); }
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse crearArticulo(ContextoUsuario contexto,
                                                            ConocimientoDtos.ArticuloRequest request) {
        validarCategoria(contexto, request.categoriaId());
        ArticuloConocimiento a = new ArticuloConocimiento(contexto.empresaId(), request.categoriaId(),
                requerido(request.titulo()), slug(request.slug(), request.titulo()),
                requerido(request.contenido()), request.visibleClientes(), contexto.usuarioId());
        a.identificar(secuencias.siguiente(SECUENCIA_ARTICULOS));
        try { return ConocimientoDtos.ArticuloResponse.from(articuloRepository.insertar(a)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictoException("Ya existe un artículo con ese identificador"); }
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse actualizar(ContextoUsuario contexto, Long id,
                                                         ConocimientoDtos.ArticuloRequest request) {
        validarCategoria(contexto, request.categoriaId());
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.actualizar(request.categoriaId(), requerido(request.titulo()), slug(request.slug(), request.titulo()),
                requerido(request.contenido()), request.visibleClientes());
        return ConocimientoDtos.ArticuloResponse.from(guardar(a));
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse publicar(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.publicar(LocalDateTime.now(BOGOTA));
        return ConocimientoDtos.ArticuloResponse.from(guardar(a));
    }

    @Transactional
    public ConocimientoDtos.ArticuloResponse archivar(ContextoUsuario contexto, Long id) {
        ArticuloConocimiento a = articuloEmpresa(contexto, id);
        a.archivar();
        return ConocimientoDtos.ArticuloResponse.from(guardar(a));
    }

    private ArticuloConocimiento guardar(ArticuloConocimiento articulo) {
        try {
            return articuloRepository.guardar(articulo);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictoException("El artículo fue modificado por otro usuario");
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("Ya existe un artículo con ese identificador");
        }
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
