package com.inmobiliaria.inmobiliaria_api.service.compartido.almacenamiento;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Archivos en S3: clave con prefijo, cifrado en reposo y errores claros. */
class AlmacenamientoS3Test {

    private final S3Client s3 = mock(S3Client.class);
    private final AlmacenamientoS3 almacenamiento = new AlmacenamientoS3(s3, "nexora-archivos", "archivos/");

    @Test
    void subeConPrefijoTipoYCifrado() {
        almacenamiento.guardar("3/7/a.pdf", new byte[] {1, 2}, "application/pdf");

        ArgumentCaptor<PutObjectRequest> pedido = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(pedido.capture(), any(RequestBody.class));
        assertThat(pedido.getValue().bucket()).isEqualTo("nexora-archivos");
        assertThat(pedido.getValue().key()).isEqualTo("archivos/3/7/a.pdf");
        assertThat(pedido.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(pedido.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    }

    @Test
    void abreElContenido() throws Exception {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hola".getBytes(StandardCharsets.UTF_8)));

        assertThat(almacenamiento.abrir("3/7/a.pdf").getContentAsByteArray())
                .asString(StandardCharsets.UTF_8).isEqualTo("hola");
    }

    @Test
    void siNoExisteEsNoEncontrado() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> almacenamiento.abrir("3/7/x.pdf")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void siS3FallaAlSubirElErrorEsClaro() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("sin red"));

        assertThatThrownBy(() -> almacenamiento.guardar("3/7/a.pdf", new byte[] {1}, "application/pdf"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("almacenar");
    }

    @Test
    void sinBucketNoArranca() {
        assertThatThrownBy(() -> new AlmacenamientoS3(s3, " ", "archivos/")).isInstanceOf(IllegalStateException.class);
    }
}
