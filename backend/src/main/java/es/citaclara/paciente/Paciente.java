package es.citaclara.paciente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ficha de paciente (FR-019). El teléfono se guarda normalizado y es único por clínica (FR-020). */
@Entity
@Table(name = "paciente")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinica_id", nullable = false)
    private Long clinicaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(length = 254)
    private String email;

    protected Paciente() {
    }

    public Paciente(Long clinicaId, String nombre, String telefono, String email) {
        this.clinicaId = clinicaId;
        this.nombre = nombre.strip();
        this.telefono = telefono;
        this.email = (email == null || email.isBlank()) ? null : email.strip();
    }

    public Long getId() {
        return id;
    }

    public Long getClinicaId() {
        return clinicaId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }
}
