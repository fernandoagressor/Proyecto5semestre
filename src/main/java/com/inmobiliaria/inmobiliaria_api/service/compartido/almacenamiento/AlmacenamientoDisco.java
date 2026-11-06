package com.inmobiliaria.inmobiliaria_api.service.compartido.almacenamiento;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Archivos en una carpeta del servidor ({@code app.storage.path}): desarrollo, pruebas y staging. */
@Component
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "disco", matchIfMissing = true)
public class AlmacenamientoDisco implements AlmacenamientoArchivos {

    private final Path raiz;

    public AlmacenamientoDisco(@Value("${app.storage.path:./storage}") String ruta) {
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
    }

    @Override
    public void guardar(String clave, byte[] contenido, String mimeType) {
        Path destino = resolver(clave);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(new ByteArrayInputStream(contenido), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ReglaNegocioException("No fue posible almacenar el documento");
        }
    }

    @Override
    public Resource abrir(String clave) {
        Path ruta = resolver(clave);
        if (!Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("El contenido del documento no está disponible");
        }
        return new FileSystemResource(ruta);
    }

    @Override
    public void eliminarSilencioso(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException | RuntimeException ignored) {
            // La limpieza no debe ocultar el error original.
        }
    }

    private Path resolver(String clave) {
        Path ruta = raiz.resolve(clave).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new ReglaNegocioException("La ubicación del documento no es válida");
        }
        return ruta;
    }
}
