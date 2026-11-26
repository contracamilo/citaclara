package es.citaclara.clinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Clínica: contiene a todas las demás entidades (FR-003). La clave solo se guarda con hash (FR-001). */
@Entity
@Table(name = "clinica")
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String identificador;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    protected Clinica() {
    }

    public Clinica(String identificador, String nombre, String claveHash) {
        this.identificador = identificador;
        this.nombre = nombre;
        this.claveHash = claveHash;
    }

    public Long getId() {
        return id;
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNombre() {
        return nombre;
    }

    public String getClaveHash() {
        return claveHash;
    }
}
