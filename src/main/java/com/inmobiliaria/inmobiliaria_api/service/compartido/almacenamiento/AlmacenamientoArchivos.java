package com.inmobiliaria.inmobiliaria_api.service.compartido.almacenamiento;

import org.springframework.core.io.Resource;

/**
 * Dónde viven los archivos subidos (contratos, comprobantes, fotos). La ubicación de cada archivo
 * es una clave relativa ("empresa/proyecto/uuid.pdf") que se guarda en la tabla {@code archivos};
 * el contenido va al disco del servidor ({@code app.storage.tipo=disco}, por defecto) o a un
 * bucket de S3 ({@code app.storage.tipo=s3}, producción).
 */
public interface AlmacenamientoArchivos {

    void guardar(String clave, byte[] contenido, String mimeType);

    /** El contenido del archivo; lanza RecursoNoEncontradoException si no existe. */
    Resource abrir(String clave);

    /** Borra sin fallar (se usa para deshacer cuando no se pudo registrar el archivo). */
    void eliminarSilencioso(String clave);
}
