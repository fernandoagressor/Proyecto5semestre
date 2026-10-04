package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "lotes")
public class Lote extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "etapa_id", nullable = false)
    private Long etapaId;

    @Column(name = "manzana_id", nullable = false)
    private Long manzanaId;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(name = "area_m2", nullable = false, precision = 12, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "frente_m", precision = 10, scale = 2)
    private BigDecimal frenteM;

    @Column(name = "fondo_m", precision = 10, scale = 2)
    private BigDecimal fondoM;

    @Column(name = "valor_lista", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLote estado;

    @Column(nullable = false)

    private boolean disponible;

    @Column(name = "ubicacion_referencia", length = 300)
    private String ubicacionReferencia;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Lote() {
    }

    public Lote(
            Long empresaId,
            Long proyectoId,
            Long etapaId,
            Long manzanaId,
            String codigo,
            String numero,
            BigDecimal areaM2,
            BigDecimal frenteM,
            BigDecimal fondoM,
            BigDecimal valorLista,
            String ubicacionReferencia,
            String descripcion
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.etapaId = etapaId;
        this.manzanaId = manzanaId;
        this.estado = EstadoLote.DISPONIBLE;
        this.disponible = true;
        this.valorLista = valorLista;
        actualizarDatos(
                codigo,
                numero,
                areaM2,
                frenteM,
                fondoM,
                ubicacionReferencia,
                descripcion
        );
    }

    public void actualizarDatos(
            String codigo,
            String numero,
            BigDecimal areaM2,
            BigDecimal frenteM,
            BigDecimal fondoM,
            String ubicacionReferencia,
            String descripcion
    ) {
        if (estado == EstadoLote.VENDIDO) {
            throw new ReglaNegocioException(
                    "No se pueden editar los datos físicos de un lote vendido"
            );
        }
        this.codigo = codigo;
        this.numero = numero;
        this.areaM2 = frenteM != null && fondoM != null
                ? frenteM.multiply(fondoM).setScale(2, RoundingMode.HALF_UP)
                : areaM2;
        this.frenteM = frenteM;
        this.fondoM = fondoM;
        this.ubicacionReferencia = ubicacionReferencia;
        this.descripcion = descripcion;
    }

    public EstadoLote cambiarEstadoManual(EstadoLote nuevoEstado) {
        if (nuevoEstado != EstadoLote.DISPONIBLE
                && nuevoEstado != EstadoLote.BLOQUEADO) {
            throw new ReglaNegocioException(
                    "Los estados RESERVADO y VENDIDO solo cambian mediante su proceso transaccional"
            );
        }
        if (estado == EstadoLote.RESERVADO || estado == EstadoLote.VENDIDO) {
            throw new ReglaNegocioException(
                    "Un lote reservado o vendido no admite cambios manuales de estado"
            );
        }
        EstadoLote anterior = estado;
        estado = nuevoEstado;
        disponible = nuevoEstado == EstadoLote.DISPONIBLE;
        return anterior;
    }

    public EstadoLote reservar() {
        if (estado != EstadoLote.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "Solo un lote disponible puede reservarse"
            );
        }
        EstadoLote anterior = estado;
        estado = EstadoLote.RESERVADO;
        disponible = false;
        return anterior;
    }

    public EstadoLote vender() {
        if (estado != EstadoLote.RESERVADO) {
            throw new ReglaNegocioException(
                    "Solo un lote reservado puede marcarse como vendido"
            );
        }
        EstadoLote anterior = estado;
        estado = EstadoLote.VENDIDO;
        disponible = false;
        return anterior;
    }

    public EstadoLote liberarReserva() {
        if (estado != EstadoLote.RESERVADO) {
            throw new ReglaNegocioException(
                    "Solo un lote reservado puede liberarse"
            );
        }
        EstadoLote anterior = estado;
        estado = EstadoLote.DISPONIBLE;
        disponible = true;
        return anterior;
    }

    public EstadoLote revertirVentaAnulada() {
        if (estado != EstadoLote.VENDIDO) {
            throw new ReglaNegocioException(
                    "Solo un lote vendido puede liberarse por anulación"
            );
        }
        EstadoLote anterior = estado;
        estado = EstadoLote.DISPONIBLE;
        disponible = true;
        return anterior;
    }

    public BigDecimal cambiarPrecio(BigDecimal nuevoValor) {
        if (estado == EstadoLote.RESERVADO || estado == EstadoLote.VENDIDO) {
            throw new ReglaNegocioException(
                    "El precio de un lote reservado o vendido no puede modificarse"
            );
        }
        if (nuevoValor == null || nuevoValor.signum() < 0) {
            throw new ReglaNegocioException("El nuevo precio no puede ser negativo");
        }
        if (nuevoValor.compareTo(valorLista) == 0) {
            throw new ReglaNegocioException("El nuevo precio debe ser diferente al actual");
        }
        BigDecimal anterior = valorLista;
        valorLista = nuevoValor;
        return anterior;
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public Long getEtapaId() {
        return etapaId;
    }

    public Long getManzanaId() {
        return manzanaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNumero() {
        return numero;
    }

    public BigDecimal getAreaM2() {
        return areaM2;
    }

    public BigDecimal getFrenteM() {
        return frenteM;
    }

    public BigDecimal getFondoM() {
        return fondoM;
    }

    public BigDecimal getValorLista() {
        return valorLista;
    }

    public EstadoLote getEstado() {
        return estado;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public String getUbicacionReferencia() {
        return ubicacionReferencia;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Long getVersion() {
        return version;
    }
}
