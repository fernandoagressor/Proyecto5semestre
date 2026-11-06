package com.inmobiliaria.inmobiliaria_api.service.compartido.almacenamiento;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Archivos en un bucket privado de S3 (producción). Las credenciales salen del rol de la EC2
 * (cadena por defecto del SDK): en el servidor no se guardan claves. Cada objeto se cifra en
 * reposo (SSE-S3) y queda bajo {@code app.storage.s3.prefijo}.
 */
@Component
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "s3")
public class AlmacenamientoS3 implements AlmacenamientoArchivos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoS3.class);

    private final S3Client s3;
    private final String bucket;
    private final String prefijo;

    public AlmacenamientoS3(
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:us-east-2}") String region,
            @Value("${app.storage.s3.prefijo:archivos/}") String prefijo
    ) {
        this(S3Client.builder().region(Region.of(region.trim())).build(), bucket, prefijo);
    }

    AlmacenamientoS3(S3Client s3, String bucket, String prefijo) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Con app.storage.tipo=s3 defina APP_STORAGE_S3_BUCKET");
        }
        this.s3 = s3;
        this.bucket = bucket.trim();
        this.prefijo = prefijo == null ? "" : prefijo.trim();
    }

    @Override
    public void guardar(String clave, byte[] contenido, String mimeType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefijo + clave)
                            .contentType(mimeType)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(contenido));
        } catch (SdkException ex) {
            log.error("No se pudo subir {} a s3://{}: {}", clave, bucket, ex.getMessage());
            throw new ReglaNegocioException("No fue posible almacenar el documento");
        }
    }

    @Override
    public Resource abrir(String clave) {
        try {
            byte[] contenido = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(prefijo + clave)
                    .build()).asByteArray();
            return new ByteArrayResource(contenido);
        } catch (NoSuchKeyException ex) {
            throw new RecursoNoEncontradoException("El contenido del documento no está disponible");
        } catch (SdkException ex) {
            log.error("No se pudo leer {} de s3://{}: {}", clave, bucket, ex.getMessage());
            throw new RecursoNoEncontradoException("El contenido del documento no está disponible");
        }
    }

    @Override
    public void eliminarSilencioso(String clave) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(prefijo + clave).build());
        } catch (RuntimeException ignored) {
            // La limpieza no debe ocultar el error original.
        }
    }

    @PreDestroy
    void cerrar() {
        s3.close();
    }
}
