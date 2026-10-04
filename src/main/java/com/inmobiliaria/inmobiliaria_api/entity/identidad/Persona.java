package com.inmobiliaria.inmobiliaria_api.entity.identidad;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "personas")
public class Persona extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_persona", nullable = false, length = 15)
    private String tipoPersona;

    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 40)
    private String numeroDocumento;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(length = 120)
    private String apellidos;

    @Column(length = 40)
    private String telefono;

    @Column(length = 180)
    private String correo;

    @Column(length = 240)
    private String direccion;

    @Column(nullable = false)

    private boolean activo;

    protected Persona() {
    }

    public Persona(
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            String correo
    ) {
        this("NATURAL", tipoDocumento, numeroDocumento, nombres, apellidos,
                null, correo, null);
    }

    public Persona(
            String tipoPersona,
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            String telefono,
            String correo,
            String direccion
    ) {
        actualizar(tipoPersona, tipoDocumento, numeroDocumento, nombres,
                apellidos, telefono, correo, direccion);
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getTipoPersona() {
        return tipoPersona;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void actualizar(
            String nuevoTipoPersona,
            String nuevoTipoDocumento,
            String nuevoNumeroDocumento,
            String nuevosNombres,
            String nuevosApellidos,
            String nuevoTelefono,
            String nuevoCorreo,
            String nuevaDireccion
    ) {
        tipoPersona = requerido(nuevoTipoPersona).toUpperCase();
        tipoDocumento = requerido(nuevoTipoDocumento).toUpperCase();
        numeroDocumento = requerido(nuevoNumeroDocumento);
        nombres = requerido(nuevosNombres);
        apellidos = opcional(nuevosApellidos);
        telefono = opcional(nuevoTelefono);
        correo = opcional(nuevoCorreo);
        if (correo != null) {
            correo = correo.toLowerCase();
        }
        direccion = opcional(nuevaDireccion);
        activo = true;
    }

    public void actualizarDato(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El nuevo valor es obligatorio");
        }
        String normalizado = valor.trim();
        switch (campo) {
            case "CORREO" -> correo = normalizado.toLowerCase();
            case "TELEFONO" -> telefono = normalizado;
            case "DIRECCION" -> direccion = normalizado;
            case "NOMBRES" -> nombres = normalizado;
            case "APELLIDOS" -> apellidos = normalizado;
            case "DOCUMENTO" -> numeroDocumento = normalizado;
            default -> throw new IllegalArgumentException("El campo solicitado no puede modificarse");
        }
    }

    private static String requerido(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El valor es obligatorio");
        }
        return valor.trim();
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
