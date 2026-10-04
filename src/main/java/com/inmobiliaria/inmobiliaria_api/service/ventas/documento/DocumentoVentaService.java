package com.inmobiliaria.inmobiliaria_api.service.ventas.documento;

import com.inmobiliaria.inmobiliaria_api.dto.ventas.documento.DocumentoVentaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentoVentaService {

    private static final long MAXIMO_BYTES = 15L * 1024L * 1024L;
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ProyectoRepository proyectoRepository;
    private final AlcanceProyectoService alcanceProyectoService;
    private final Path raiz;

    public DocumentoVentaService(
            JdbcTemplate jdbcTemplate,
            ProyectoRepository proyectoRepository,
            AlcanceProyectoService alcanceProyectoService,
            @Value("${app.storage.path:./storage}") String ruta
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.proyectoRepository = proyectoRepository;
        this.alcanceProyectoService = alcanceProyectoService;
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
    }

    @Transactional
    public DocumentoVentaResponse guardar(
            ContextoUsuario contexto,
            Long proyectoId,
            String clasificacion,
            MultipartFile archivo
    ) {
        validarProyecto(contexto, proyectoId);
        return guardarValidado(contexto, proyectoId, clasificacion, archivo);
    }

    /**
     * Almacena un archivo después de que el módulo solicitante validó el acceso
     * a su entidad de negocio. No debe exponerse directamente desde un
     * controlador.
     */
    @Transactional
    public DocumentoVentaResponse guardarAdjuntoSolicitudAutorizada(
            ContextoUsuario contexto,
            Long proyectoId,
            MultipartFile archivo
    ) {
        return guardarValidado(contexto, proyectoId, "CONFIDENCIAL", archivo);
    }

    private DocumentoVentaResponse guardarValidado(
            ContextoUsuario contexto,
            Long proyectoId,
            String clasificacion,
            MultipartFile archivo
    ) {
        validarArchivo(archivo);
        String nombreOriginal = nombreSeguro(archivo.getOriginalFilename());
        String mimeType = archivo.getContentType().toLowerCase(Locale.ROOT);
        String clasificacionNormalizada = normalizarClasificacion(clasificacion);
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new ReglaNegocioException("No fue posible leer el documento");
        }
        String hash = sha256(contenido);
        String nombreAlmacenado = UUID.randomUUID() + extension(nombreOriginal);
        String rutaRelativa = contexto.empresaId() + "/"
                + (proyectoId == null ? "general" : proyectoId) + "/"
                + nombreAlmacenado;
        Path destino = raiz.resolve(rutaRelativa).normalize();
        if (!destino.startsWith(raiz)) {
            throw new ReglaNegocioException("La ubicación del documento no es válida");
        }
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(
                    new java.io.ByteArrayInputStream(contenido),
                    destino,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException ex) {
            throw new ReglaNegocioException("No fue posible almacenar el documento");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO archivos
                            (empresa_id, proyecto_id, subido_por, nombre_original,
                             nombre_almacenado, ubicacion, mime_type, tamano_bytes,
                             sha256, clasificacion, estado)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, contexto.empresaId());
                if (proyectoId == null) statement.setNull(2, java.sql.Types.BIGINT);
                else statement.setLong(2, proyectoId);
                statement.setLong(3, contexto.usuarioId());
                statement.setString(4, nombreOriginal);
                statement.setString(5, nombreAlmacenado);
                statement.setString(6, rutaRelativa.replace('\\', '/'));
                statement.setString(7, mimeType);
                statement.setLong(8, contenido.length);
                statement.setString(9, hash);
                statement.setString(10, clasificacionNormalizada);
                return statement;
            }, keyHolder);
        } catch (RuntimeException ex) {
            eliminarSilencioso(destino);
            throw ex;
        }
        Number clave = keyHolder.getKey();
        if (clave == null) {
            eliminarSilencioso(destino);
            throw new ReglaNegocioException("No fue posible registrar el documento");
        }
        return new DocumentoVentaResponse(
                clave.longValue(), contexto.empresaId(), proyectoId,
                nombreOriginal, mimeType, contenido.length, hash,
                clasificacionNormalizada, LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public Descarga descargar(ContextoUsuario contexto, Long archivoId) {
        DocumentoAlmacenado documento = buscarDocumento(contexto, archivoId);
        validarProyecto(contexto, documento.proyectoId());
        return abrir(documento);
    }

    /**
     * Abre un archivo después de que el módulo propietario comprobó que el
     * usuario puede consultar la solicitud o el documento que lo referencia.
     */
    @Transactional(readOnly = true)
    public Descarga descargarEntidadAutorizada(ContextoUsuario contexto, Long archivoId) {
        return abrir(buscarDocumento(contexto, archivoId));
    }

    private DocumentoAlmacenado buscarDocumento(ContextoUsuario contexto, Long archivoId) {
        return jdbcTemplate.query("""
                        SELECT proyecto_id, nombre_original, ubicacion, mime_type
                        FROM archivos
                        WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'
                        """,
                (rs, rowNum) -> new DocumentoAlmacenado(
                        rs.getObject("proyecto_id", Long.class),
                        rs.getString("nombre_original"),
                        rs.getString("ubicacion"),
                        rs.getString("mime_type")
                ),
                archivoId,
                contexto.empresaId()
        ).stream().findFirst().orElseThrow(() ->
                new RecursoNoEncontradoException("El documento no existe"));
    }

    private Descarga abrir(DocumentoAlmacenado documento) {
        Path ruta = raiz.resolve(documento.ubicacion()).normalize();
        if (!ruta.startsWith(raiz) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException(
                    "El contenido del documento no está disponible"
            );
        }
        return new Descarga(
                documento.nombreOriginal(),
                documento.mimeType(),
                new FileSystemResource(ruta)
        );
    }

    private void validarProyecto(ContextoUsuario contexto, Long proyectoId) {
        if (proyectoId == null) {
            alcanceProyectoService.verificarEmpresa(
                    contexto.usuarioId(), contexto.empresaId());
            return;
        }
        Proyecto proyecto = proyectoRepository.findByIdAndEmpresaId(
                proyectoId, contexto.empresaId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El proyecto no pertenece a la empresa activa"
        ));
        alcanceProyectoService.verificarProyecto(
                contexto.usuarioId(), contexto.empresaId(), proyecto);
    }

    private static void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Debe seleccionar un documento");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new ReglaNegocioException("El documento supera el límite de 15 MB");
        }
        String mime = archivo.getContentType();
        if (mime == null || !MIME_PERMITIDOS.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new ReglaNegocioException("Solo se permiten archivos PDF, PNG o JPG");
        }
    }

    private static String normalizarClasificacion(String clasificacion) {
        String valor = clasificacion == null
                ? "CONFIDENCIAL"
                : clasificacion.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PUBLICO", "INTERNO", "CONFIDENCIAL", "RESTRINGIDO").contains(valor)) {
            throw new ReglaNegocioException("La clasificación no es válida");
        }
        return valor;
    }

    private static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) return "documento";
        String limpio = Path.of(nombre).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (limpio.isBlank()) return "documento";
        return limpio.length() > 255 ? limpio.substring(0, 255) : limpio;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) return "";
        String extension = nombre.substring(punto).toLowerCase(Locale.ROOT);
        return extension.length() <= 10 ? extension : "";
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private static void eliminarSilencioso(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignored) {
            // La limpieza no debe ocultar el error original de persistencia.
        }
    }

    private record DocumentoAlmacenado(
            Long proyectoId,
            String nombreOriginal,
            String ubicacion,
            String mimeType
    ) {
    }

    public record Descarga(String nombre, String mimeType, Resource recurso) {
    }
}
