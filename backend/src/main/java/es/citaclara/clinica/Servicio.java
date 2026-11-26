package es.citaclara.clinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinica_id", nullable = false)
    private Long clinicaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 40)
    private String especialidad;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    /** Importe en céntimos enteros (research D5). */
    @Column(name = "precio_centimos", nullable = false)
    private int precioCentimos;

    protected Servicio() {
    }

    public Servicio(Long clinicaId, String nombre, String especialidad, int duracionMinutos, int precioCentimos) {
        this.clinicaId = clinicaId;
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.duracionMinutos = duracionMinutos;
        this.precioCentimos = precioCentimos;
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

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public int getPrecioCentimos() {
        return precioCentimos;
    }
}
