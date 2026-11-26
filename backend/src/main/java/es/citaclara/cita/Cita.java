package es.citaclara.cita;

import es.citaclara.clinica.Profesional;
import es.citaclara.clinica.Servicio;
import es.citaclara.paciente.Paciente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Cita. Inicio y fin son instantes (timestamptz, research D4); el importe es la copia en céntimos
 * del precio al reservar (FR-017). La base de datos impide solapes (cita_sin_solape, FR-012).
 */
@Entity
@Table(name = "cita")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinica_id", nullable = false)
    private Long clinicaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(name = "importe_centimos", nullable = false)
    private int importeCentimos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCita estado;

    /** Bloqueo optimista de los cambios de estado (FR-025, research D3). */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    protected Cita() {
    }

    public Cita(Long clinicaId, Profesional profesional, Servicio servicio, Paciente paciente,
                Instant inicio, Instant fin, int importeCentimos, EstadoCita estado, Instant creadaEn) {
        this.clinicaId = clinicaId;
        this.profesional = profesional;
        this.servicio = servicio;
        this.paciente = paciente;
        this.inicio = inicio;
        this.fin = fin;
        this.importeCentimos = importeCentimos;
        this.estado = estado;
        this.creadaEn = creadaEn;
    }

    void cambiarEstado(EstadoCita nuevo) {
        this.estado = nuevo;
    }

    public Long getId() {
        return id;
    }

    public Long getClinicaId() {
        return clinicaId;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public Servicio getServicio() {
        return servicio;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public int getImporteCentimos() {
        return importeCentimos;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public int getVersion() {
        return version;
    }
}
