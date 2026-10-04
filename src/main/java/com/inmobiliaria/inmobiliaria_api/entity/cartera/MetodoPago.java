package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "metodos_pago")
public class MetodoPago extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id")
    private Long empresaId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(name = "requiere_referencia", nullable = false)
    private boolean requiereReferencia;
    @Column(name = "requiere_comprobante", nullable = false)
    private boolean requiereComprobante;
    @Column(nullable = false)
    private boolean activo;

    protected MetodoPago() {
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public boolean isRequiereReferencia() { return requiereReferencia; }
    public boolean isRequiereComprobante() { return requiereComprobante; }
    public boolean isActivo() { return activo; }
}
