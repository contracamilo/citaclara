package es.citaclara.recordatorio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Recordatorio generado para una cita (data-model.md). Una fila existe solo si el correo se generó
 * y se guardó (FR-007); {@code UNIQUE(cita_id)} impide duplicados (FR-004). Solo se guarda la huella
 * del enlace de cancelación, nunca el enlace.
 */
@Entity
@Table(name = "recordatorio")
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinica_id", nullable = false)
    private Long clinicaId;

    @Column(name = "cita_id", nullable = false)
    private Long citaId;

    @Column(name = "email_destino", nullable = false, length = 254)
    private String emailDestino;

    @Column(name = "enlace_huella", nullable = false, length = 64)
    private String enlaceHuella;

    @Column(name = "generado_en", nullable = false)
    private Instant generadoEn;

    protected Recordatorio() {
    }

    public Long getId() {
        return id;
    }

    public Long getClinicaId() {
        return clinicaId;
    }

    public Long getCitaId() {
        return citaId;
    }

    public String getEmailDestino() {
        return emailDestino;
    }

    public String getEnlaceHuella() {
        return enlaceHuella;
    }

    public Instant getGeneradoEn() {
        return generadoEn;
    }
}
