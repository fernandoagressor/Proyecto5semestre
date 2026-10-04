package com.inmobiliaria.inmobiliaria_api.entity.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "intentos_acceso")
public class IntentoAcceso extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 180)
    private String identificador;

    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(length = 200)
    private String motivo;

    @Column(name = "ip_origen", length = 64)
    private String ipOrigen;

    @Column(name = "agente_usuario", length = 500)
    private String agenteUsuario;

    @Column(name = "fecha_intento", nullable = false)
    private LocalDateTime fechaIntento;

    protected IntentoAcceso() {
    }

    public IntentoAcceso(
            Long usuarioId,
            String identificador,
            String resultado,
            String motivo,
            String ipOrigen,
            String agenteUsuario,
            LocalDateTime fechaIntento
    ) {
        this.usuarioId = usuarioId;
        this.identificador = identificador;
        this.resultado = resultado;
        this.motivo = motivo;
        this.ipOrigen = ipOrigen;
        this.agenteUsuario = agenteUsuario;
        this.fechaIntento = fechaIntento;
    }
}
