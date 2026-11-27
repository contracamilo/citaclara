package es.citaclara.agenda;

import es.citaclara.cita.CitaRespuesta;
import es.citaclara.clinica.ProfesionalRespuesta;

import java.time.LocalDate;
import java.util.List;

/** Esquema {@code Agenda} de contracts/api.yaml. {@code horario} es nulo si la clínica no atiende. */
public record AgendaRespuesta(LocalDate fecha, LocalDate hoy, ProfesionalRespuesta profesional,
                              Horario horario, List<CitaRespuesta> citas, List<HuecoRespuesta> huecos) {

    public record Horario(String apertura, String cierre) {
    }

    public record HuecoRespuesta(String horaInicio, String horaFin, boolean reservable) {
    }
}
