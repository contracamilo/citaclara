package es.citaclara.clinica;

import es.citaclara.acceso.SesionClinica;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contexto de la sesión: clínica, catálogo, horario y «hoy» (contracts/api.yaml, esquema Contexto). */
@RestController
public class ControladorContexto {

    private final RepositorioClinica clinicas;
    private final RepositorioProfesional profesionales;
    private final RepositorioServicio servicios;
    private final RepositorioHorario horarios;
    private final Clock reloj;

    public ControladorContexto(RepositorioClinica clinicas, RepositorioProfesional profesionales,
                               RepositorioServicio servicios, RepositorioHorario horarios, Clock reloj) {
        this.clinicas = clinicas;
        this.profesionales = profesionales;
        this.servicios = servicios;
        this.horarios = horarios;
        this.reloj = reloj;
    }

    public record Contexto(ClinicaRef clinica, LocalDate hoy, Instant sesionCaducaA,
                           List<ProfesionalRespuesta> profesionales, List<ServicioRespuesta> servicios,
                           List<HorarioRespuesta> horario) {
    }

    public record ClinicaRef(String nombre) {
    }

    public record HorarioRespuesta(int diaSemana, String apertura, String cierre) {
    }

    @GetMapping("/api/sesion")
    @Transactional(readOnly = true)
    public Contexto contexto(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                             @RequestAttribute(SesionClinica.CADUCA_A) Instant caducaA) {
        Clinica clinica = clinicas.findById(clinicaId).orElseThrow(ErrorNegocio::noEncontrado);
        return new Contexto(
                new ClinicaRef(clinica.getNombre()),
                ZonaClinica.hoy(reloj),
                caducaA,
                profesionales.findByClinicaIdOrderByNombre(clinicaId).stream().map(ProfesionalRespuesta::desde).toList(),
                servicios.findByClinicaIdOrderByEspecialidadAscNombreAsc(clinicaId).stream()
                        .map(ServicioRespuesta::desde).toList(),
                horarios.findByClinicaIdOrderByDiaSemana(clinicaId).stream()
                        .map(h -> new HorarioRespuesta(h.getDiaSemana(), ZonaClinica.formatoHora(h.getApertura()),
                                ZonaClinica.formatoHora(h.getCierre())))
                        .toList());
    }
}
