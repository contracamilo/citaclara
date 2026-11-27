package es.citaclara.clinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;

/** Horario semanal de atención (hora local de Madrid). Un día sin fila = clínica cerrada. */
@Entity
@Table(name = "horario_atencion")
@IdClass(HorarioAtencion.Clave.class)
public class HorarioAtencion {

    @Id
    @Column(name = "clinica_id")
    private Long clinicaId;

    /** ISO-8601: 1 = lunes … 7 = domingo. */
    @Id
    @Column(name = "dia_semana")
    private Short diaSemana;

    @Column(nullable = false)
    private LocalTime apertura;

    @Column(nullable = false)
    private LocalTime cierre;

    protected HorarioAtencion() {
    }

    public HorarioAtencion(Long clinicaId, int diaSemana, LocalTime apertura, LocalTime cierre) {
        this.clinicaId = clinicaId;
        this.diaSemana = (short) diaSemana;
        this.apertura = apertura;
        this.cierre = cierre;
    }

    public Long getClinicaId() {
        return clinicaId;
    }

    public int getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getApertura() {
        return apertura;
    }

    public LocalTime getCierre() {
        return cierre;
    }

    public static class Clave implements Serializable {
        private Long clinicaId;
        private Short diaSemana;

        public Clave() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(clinicaId, c.clinicaId) && Objects.equals(diaSemana, c.diaSemana);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clinicaId, diaSemana);
        }
    }
}
