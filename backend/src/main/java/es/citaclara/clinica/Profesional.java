package es.citaclara.clinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profesional")
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinica_id", nullable = false)
    private Long clinicaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Valor interno en minúsculas (fisioterapia, nutricion); el texto visible sale de {@link Especialidades}. */
    @Column(nullable = false, length = 40)
    private String especialidad;

    protected Profesional() {
    }

    public Profesional(Long clinicaId, String nombre, String especialidad) {
        this.clinicaId = clinicaId;
        this.nombre = nombre;
        this.especialidad = especialidad;
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

    public String getEspecialidad() {
        return especialidad;
    }
}
