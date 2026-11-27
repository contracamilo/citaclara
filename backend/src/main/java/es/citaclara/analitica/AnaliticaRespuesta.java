package es.citaclara.analitica;

import es.citaclara.analitica.PeriodoAnalitica.Semana;

import java.util.List;

/** Respuesta de {@code GET /api/analitica} (specs/004-panel-analitica/contracts/api.yaml). */
public record AnaliticaRespuesta(boolean sinDatos, UltimaSemana ultimaSemana, Ingresos ingresos,
                                 List<SemanaEvolucion> evolucion) {

    public record UltimaSemana(Semana semana, List<ProfesionalAnalitica> profesionales) {
    }

    public record ProfesionalAnalitica(long profesionalId, String nombre, String especialidadNombre,
                                       Proporcion tasaNoAsistencia, Proporcion ocupacion) {
    }

    public record Ingresos(List<ServicioIngresos> servicios, long totalCentimos, long totalNoObtenidoCentimos) {
    }

    public record ServicioIngresos(long servicioId, String nombre, int citasCompletadas, long importeCentimos,
                                   int citasNoAsistidas, long importeNoObtenidoCentimos) {
    }

    public record SemanaEvolucion(Semana semana, int completadas, int noAsistidas, int canceladas,
                                  Proporcion tasaNoAsistencia, long ingresosCentimos,
                                  List<OcupacionProfesional> ocupacion) {
    }

    public record OcupacionProfesional(long profesionalId, Proporcion ocupacion) {
    }
}
