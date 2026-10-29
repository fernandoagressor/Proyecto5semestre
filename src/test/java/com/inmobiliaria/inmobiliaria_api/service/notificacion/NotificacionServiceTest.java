package com.inmobiliaria.inmobiliaria_api.service.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificacionServiceTest {

    private NotificacionRepository repository;
    private SecuenciaService secuencias;
    private NotificacionService service;

    @BeforeEach
    void preparar() {
        repository = mock(NotificacionRepository.class);
        secuencias = mock(SecuenciaService.class);
        when(secuencias.siguiente("notificaciones")).thenReturn(41L);
        service = new NotificacionService(repository, secuencias);
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void fueraDeUnaTransaccionSeGuardaDeInmediato() {
        crear(5L);

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).insertar(guardada.capture());
        assertThat(guardada.getValue().getId()).isEqualTo(41L);
        assertThat(guardada.getValue().getUsuarioId()).isEqualTo(5L);
        assertThat(guardada.getValue().getCreadoEn()).isNotNull();
    }

    @Test
    void dentroDeUnaTransaccionEsperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        crear(5L);
        verify(repository, never()).insertar(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(repository).insertar(any());
    }

    @Test
    void siLaTransaccionHaceRollbackNoSeCrea() {
        TransactionSynchronizationManager.initSynchronization();

        crear(5L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(repository, never()).insertar(any());
        verify(secuencias, never()).siguiente(any());
    }

    @Test
    void sinDestinatarioNoHaceNada() {
        crear(null);

        verify(repository, never()).insertar(any());
    }

    @Test
    void unFalloAlGuardarNoSePropaga() {
        when(repository.insertar(any())).thenThrow(new IllegalStateException("MongoDB no responde"));

        assertThatCode(() -> crear(5L)).doesNotThrowAnyException();
    }

    private void crear(Long usuarioId) {
        service.crear(8L, usuarioId, "PQRS_RESPUESTA", "Nueva respuesta", "La solicitud tiene una respuesta",
                "SOLICITUD_PQRS", 12L, "MEDIA");
    }
}
