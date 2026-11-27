package es.citaclara.agenda;

import es.citaclara.agenda.CalculadoraHuecos.Intervalo;
import es.citaclara.cita.Cita;
import es.citaclara.cita.CitaRespuesta;
import es.citaclara.cita.RepositorioCita;
import es.citaclara.clinica.HorarioAtencion;
import es.citaclara.clinica.Profesional;
import es.citaclara.clinica.ProfesionalRespuesta;
import es.citaclara.clinica.RepositorioHorario;
import es.citaclara.clinica.RepositorioProfesional;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Agenda del día de un profesional (FR-005–FR-007, data-model «Agenda del día»). */
@Service
public class ServicioAgenda {

    private final RepositorioProfesional profesionales;
    private final RepositorioHorario horarios;
    private final RepositorioCita citas;
    private final Clock reloj;

    public ServicioAgenda(RepositorioProfesional profesionales, RepositorioHorario horarios,
                          RepositorioCita citas, Clock reloj) {
        this.profesionales = profesionales;
        this.horarios = horarios;
        this.citas = citas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public AgendaRespuesta agenda(Long clinicaId, Long profesionalId, LocalDate fechaPedida) {
        Profesional profesional = profesionales.findByIdAndClinicaId(profesionalId, clinicaId)
                .orElseThrow(ErrorNegocio::noEncontrado);
        Instant ahora = reloj.instant();
        LocalDate hoy = ZonaClinica.fechaLocal(ahora);
        LocalDate fecha = fechaPedida != null ? fechaPedida : hoy;

        List<Cita> delDia = citas.delProfesionalEntre(profesional.getId(),
                ZonaClinica.inicioDelDia(fecha), ZonaClinica.inicioDelDia(fecha.plusDays(1)));
        List<CitaRespuesta> citasRespuesta = delDia.stream().map(c -> CitaRespuesta.desde(c, ahora)).toList();

        Optional<HorarioAtencion> horario = horarios.delDia(clinicaId, fecha.getDayOfWeek().getValue());
        AgendaRespuesta.Horario horarioRespuesta = null;
        List<AgendaRespuesta.HuecoRespuesta> huecos = List.of();
        if (horario.isPresent()) {
            HorarioAtencion h = horario.get();
            horarioRespuesta = new AgendaRespuesta.Horario(
                    ZonaClinica.formatoHora(h.getApertura()), ZonaClinica.formatoHora(h.getCierre()));
            List<Intervalo> ocupados = delDia.stream()
                    .filter(c -> c.getEstado().ocupaHueco())
                    .map(c -> new Intervalo(c.getInicio(), c.getFin()))
                    .toList();
            huecos = CalculadoraHuecos.calcular(
                            ZonaClinica.aInstante(fecha, h.getApertura()),
                            ZonaClinica.aInstante(fecha, h.getCierre()),
                            ocupados, ahora).stream()
                    .map(hueco -> new AgendaRespuesta.HuecoRespuesta(
                            ZonaClinica.formatoHora(hueco.inicio()), ZonaClinica.formatoHora(hueco.fin()),
                            hueco.reservable()))
                    .toList();
        }
        return new AgendaRespuesta(fecha, hoy, ProfesionalRespuesta.desde(profesional), horarioRespuesta,
                citasRespuesta, huecos);
    }
}
