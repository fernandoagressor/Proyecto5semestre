package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuditoriaRegistroServiceTest {

    private EventoAuditoriaRepository repository;
    private SecuenciaService secuencias;
    private AuditoriaRegistroService service;
    private ContextoUsuario contexto;

    @BeforeEach
    void preparar() {
        repository = mock(EventoAuditoriaRepository.class);
        secuencias = mock(SecuenciaService.class);
        when(secuencias.siguiente("auditoria_eventos")).thenReturn(77L);
        service = new AuditoriaRegistroService(repository, secuencias, new ObjectMapper());
        contexto = new ContextoUsuario(34L, 8L);
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void fueraDeUnaTransaccionGuardaElEventoConSusDatos() {
        registrar();

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(repository).insertar(evento.capture());
        assertThat(evento.getValue().getId()).isEqualTo(77L);
        assertThat(evento.getValue().getEmpresaId()).isEqualTo(8L);
        assertThat(evento.getValue().getActorId()).isEqualTo(34L);
        assertThat(evento.getValue().getDatosAnterioresJson()).isEqualTo("\"PENDIENTE\"");
        assertThat(evento.getValue().getDatosNuevosJson()).isEqualTo("{\"estado\":\"GENERADO\"}");
        assertThat(evento.getValue().getCorrelationId()).hasSize(36);
        assertThat(evento.getValue().getOcurridoEn()).isNotNull();
    }

    @Test
    void dentroDeUnaTransaccionEsperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        registrar();
        verify(repository, never()).insertar(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(repository).insertar(any());
    }

    @Test
    void siLaTransaccionHaceRollbackNoQuedaEvento() {
        TransactionSynchronizationManager.initSynchronization();

        registrar();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(repository, never()).insertar(any());
    }

    @Test
    void unFalloDeMongoNoTumbaLaOperacion() {
        when(repository.insertar(any())).thenThrow(new IllegalStateException("MongoDB no responde"));

        assertThatCode(this::registrar).doesNotThrowAnyException();
    }

    private void registrar() {
        service.registrar(contexto, null, "REPORTES", "REPORTE_GENERAR", "REPORTE", 5L,
                "Reporte generado", "PENDIENTE", Map.of("estado", "GENERADO"));
    }
}
