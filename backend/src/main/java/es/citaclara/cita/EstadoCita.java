package es.citaclara.cita;

/** Estados de la cita (FR-021). Solo RESERVADA y COMPLETADA ocupan hueco (FR-024). */
public enum EstadoCita {
    RESERVADA("Reservada"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada"),
    NO_ASISTIDA("No asistida");

    private final String nombre;

    EstadoCita(String nombre) {
        this.nombre = nombre;
    }

    /** Texto visible en español de España. */
    public String nombre() {
        return nombre;
    }

    public boolean ocupaHueco() {
        return this == RESERVADA || this == COMPLETADA;
    }
}
