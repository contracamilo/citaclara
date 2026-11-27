package es.citaclara.analitica;

import es.citaclara.analitica.CalculadoraAnalitica.DatosClinica;
import es.citaclara.analitica.CalculadoraAnalitica.ProfesionalDato;
import es.citaclara.analitica.CalculadoraAnalitica.ServicioDato;
import es.citaclara.clinica.Especialidades;
import es.citaclara.clinica.HorarioAtencion;
import es.citaclara.clinica.RepositorioHorario;
import es.citaclara.clinica.RepositorioProfesional;
import es.citaclara.clinica.RepositorioServicio;
import es.citaclara.comun.ZonaClinica;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Carga de solo lectura y composición de la analítica (FR-003: nada se escribe). */
@Service
public class ServicioAnalitica {

    private final RepositorioAnalitica citas;
    private final RepositorioProfesional profesionales;
    private final RepositorioServicio servicios;
    private final RepositorioHorario horarios;
    private final Clock reloj;

    public ServicioAnalitica(RepositorioAnalitica citas, RepositorioProfesional profesionales,
                             RepositorioServicio servicios, RepositorioHorario horarios, Clock reloj) {
        this.citas = citas;
        this.profesionales = profesionales;
        this.servicios = servicios;
        this.horarios = horarios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public AnaliticaRespuesta analitica(Long clinicaId) {
        PeriodoAnalitica periodo = PeriodoAnalitica.de(ZonaClinica.hoy(reloj));
        List<CitaCerrada> cerradas = citas.cerradasEntre(clinicaId, periodo.desde(), periodo.hasta());
        DatosClinica clinica = new DatosClinica(
                profesionales.findByClinicaIdOrderByNombre(clinicaId).stream()
                        .map(p -> new ProfesionalDato(p.getId(), p.getNombre(), Especialidades.nombre(p.getEspecialidad())))
                        .toList(),
                servicios.findByClinicaIdOrderByEspecialidadAscNombreAsc(clinicaId).stream()
                        .map(s -> new ServicioDato(s.getId(), s.getNombre()))
                        .toList(),
                minutosHorarioSemana(horarios.findByClinicaIdOrderByDiaSemana(clinicaId)));
        return new AnaliticaRespuesta(
                cerradas.isEmpty(),
                CalculadoraAnalitica.ultimaSemana(periodo, cerradas, clinica),
                CalculadoraAnalitica.ingresos(periodo, cerradas, clinica),
                CalculadoraAnalitica.evolucion(periodo, cerradas, clinica));
    }

    static long minutosHorarioSemana(List<HorarioAtencion> horario) {
        return horario.stream().mapToLong(h -> Duration.between(h.getApertura(), h.getCierre()).toMinutes()).sum();
    }
}
