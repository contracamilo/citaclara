package es.citaclara.semilla;

import es.citaclara.cita.EstadoCita;

import java.time.LocalTime;

/**
 * Cita de demostración por posición relativa (día laborable respecto al día de carga) y hora local.
 * Los índices apuntan a las listas de {@link CatalogoDemostracion}.
 */
public record CitaSemilla(int posicion, int profesional, LocalTime inicio, int servicio, int paciente, EstadoCita estado) {

    public CitaSemilla conEstado(EstadoCita nuevo) {
        return new CitaSemilla(posicion, profesional, inicio, servicio, paciente, nuevo);
    }
}
