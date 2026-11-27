package es.citaclara.analitica;

import es.citaclara.analitica.AnaliticaRespuesta.ProfesionalAnalitica;
import es.citaclara.analitica.CalculadoraAnalitica.DatosClinica;
import es.citaclara.analitica.CalculadoraAnalitica.ProfesionalDato;
import es.citaclara.analitica.CalculadoraAnalitica.ServicioDato;
import es.citaclara.cita.EstadoCita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static es.citaclara.analitica.CitasPrueba.JORGE;
import static es.citaclara.analitica.CitasPrueba.MARIA;
import static es.citaclara.analitica.CitasPrueba.SESION;
import static es.citaclara.analitica.CitasPrueba.cita;
import static org.assertj.core.api.Assertions.assertThat;

/** Hoy = lunes 30/11/2026: la última semana completa es 23/11–29/11 (3.300 min de horario). */
class CalculadoraUltimaSemanaTest {

    private static final PeriodoAnalitica PERIODO = PeriodoAnalitica.de(LocalDate.of(2026, 11, 30));
    private static final DatosClinica CLINICA = new DatosClinica(
            List.of(new ProfesionalDato(MARIA, "María", "Fisioterapia"), new ProfesionalDato(JORGE, "Jorge", "Fisioterapia")),
            List.of(new ServicioDato(SESION, "Sesión fisioterapia")), 3300);

    private static ProfesionalAnalitica fila(List<CitaCerrada> citas, long profesional) {
        return CalculadoraAnalitica.ultimaSemana(PERIODO, citas, CLINICA).profesionales().stream()
                .filter(p -> p.profesionalId() == profesional).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("[004][FR-008][US1-AC2] la tasa de no asistencia es no asistidas ÷ (completadas + no asistidas + canceladas)")
    void tasaSobreTodasLasCerradas() {
        List<CitaCerrada> citas = List.of(
                cita(MARIA, SESION, "2026-11-23", "10:00", 45, 4000, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-24", "10:00", 45, 4000, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-25", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, SESION, "2026-11-26", "10:00", 45, 4000, EstadoCita.CANCELADA));
        Proporcion tasa = fila(citas, MARIA).tasaNoAsistencia();
        assertThat(tasa.parte()).isEqualTo(1);
        assertThat(tasa.total()).isEqualTo(4);
        assertThat(tasa.decimas()).isEqualTo(250);
    }

    @Test
    @DisplayName("[004][FR-008] la calculadora ignora cualquier fila reservada que llegue (defensa en profundidad)")
    void ignoraReservadas() {
        List<CitaCerrada> citas = List.of(
                cita(MARIA, SESION, "2026-11-23", "10:00", 45, 4000, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-24", "10:00", 45, 4000, EstadoCita.RESERVADA));
        ProfesionalAnalitica maria = fila(citas, MARIA);
        assertThat(maria.tasaNoAsistencia().total()).isEqualTo(1);
        assertThat(maria.ocupacion().parte()).isEqualTo(45);
    }

    @Test
    @DisplayName("[004][FR-009][US1-AC3] la ocupación son los minutos completados ÷ minutos del horario de la semana (3.300 con L–V 09:00–20:00)")
    void ocupacionSobreHorario() {
        List<CitaCerrada> citas = List.of(
                cita(MARIA, SESION, "2026-11-23", "10:00", 45, 4000, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-24", "10:00", 60, 5000, EstadoCita.COMPLETADA));
        Proporcion ocupacion = fila(citas, MARIA).ocupacion();
        assertThat(ocupacion.parte()).isEqualTo(105);
        assertThat(ocupacion.total()).isEqualTo(3300);
        assertThat(ocupacion.decimas()).isEqualTo(32); // 3,18 %
    }

    @Test
    @DisplayName("[004][FR-009] las no asistidas y las canceladas no suman minutos de ocupación")
    void soloCompletadasOcupan() {
        List<CitaCerrada> citas = List.of(
                cita(MARIA, SESION, "2026-11-23", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, SESION, "2026-11-24", "10:00", 60, 5000, EstadoCita.CANCELADA));
        assertThat(fila(citas, MARIA).ocupacion().parte()).isZero();
    }

    @Test
    @DisplayName("[004][FR-011][US1-AC4] un profesional sin citas cerradas da tasa «Sin datos» y ocupación 0")
    void sinCitas() {
        ProfesionalAnalitica jorge = fila(List.of(), JORGE);
        assertThat(jorge.tasaNoAsistencia().decimas()).isNull();
        assertThat(jorge.tasaNoAsistencia().total()).isZero();
        assertThat(jorge.ocupacion().decimas()).isZero();
        assertThat(jorge.ocupacion().total()).isEqualTo(3300);
    }

    @Test
    @DisplayName("[004][FR-011] una clínica sin horario de atención da ocupación «Sin datos», no 0 %")
    void sinHorario() {
        DatosClinica sinHorario = new DatosClinica(CLINICA.profesionales(), CLINICA.servicios(), 0);
        ProfesionalAnalitica maria = CalculadoraAnalitica.ultimaSemana(PERIODO, List.of(), sinHorario).profesionales().getFirst();
        assertThat(maria.ocupacion().decimas()).isNull();
    }

    @Test
    @DisplayName("[004][FR-004][US1-AC1] aparecen todos los profesionales de la clínica en el orden recibido, aunque no tengan citas")
    void todosLosProfesionales() {
        List<ProfesionalAnalitica> filas = CalculadoraAnalitica.ultimaSemana(PERIODO, List.of(), CLINICA).profesionales();
        assertThat(filas).extracting(ProfesionalAnalitica::nombre).containsExactly("María", "Jorge");
        assertThat(filas).extracting(ProfesionalAnalitica::especialidadNombre).containsOnly("Fisioterapia");
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC3] una cita del domingo 23:59 y otra del lunes 00:00 de Madrid caen en semanas distintas")
    void limiteDeSemana() {
        List<CitaCerrada> citas = List.of(
                cita(MARIA, SESION, "2026-11-22", "23:59", 30, 3500, EstadoCita.COMPLETADA),   // domingo: semana 7
                cita(MARIA, SESION, "2026-11-23", "00:00", 30, 3500, EstadoCita.COMPLETADA));  // lunes: semana 8
        assertThat(PERIODO.indiceDe(citas.get(0).inicio())).hasValue(6);
        assertThat(PERIODO.indiceDe(citas.get(1).inicio())).hasValue(7);
        assertThat(fila(citas, MARIA).ocupacion().parte()).isEqualTo(30); // solo la del lunes es de la última semana
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC3] las citas de la noche del cambio de hora (25/10/2026) no se pierden ni se duplican")
    void cambioDeHora() {
        PeriodoAnalitica periodo = PeriodoAnalitica.de(LocalDate.of(2026, 11, 2)); // semana 7 = 19/10–25/10
        List<CitaCerrada> citas = List.of(
                CitasPrueba.enInstante(MARIA, SESION, Instant.parse("2026-10-24T21:30:00Z"), 30, 3500, EstadoCita.COMPLETADA), // sáb 23:30 CEST
                CitasPrueba.enInstante(MARIA, SESION, Instant.parse("2026-10-25T00:30:00Z"), 30, 3500, EstadoCita.COMPLETADA), // 02:30 CEST
                CitasPrueba.enInstante(MARIA, SESION, Instant.parse("2026-10-25T01:30:00Z"), 30, 3500, EstadoCita.COMPLETADA), // 02:30 CET (repetida)
                CitasPrueba.enInstante(MARIA, SESION, Instant.parse("2026-10-25T22:59:00Z"), 30, 3500, EstadoCita.COMPLETADA), // dom 23:59 CET
                CitasPrueba.enInstante(MARIA, SESION, Instant.parse("2026-10-25T23:00:00Z"), 30, 3500, EstadoCita.COMPLETADA)); // lun 00:00 CET
        List<Integer> semanas = citas.stream().map(c -> periodo.indiceDe(c.inicio()).orElse(-1)).toList();
        assertThat(semanas).containsExactly(6, 6, 6, 6, 7);
        int total = CalculadoraAnalitica.evolucion(periodo, citas, CLINICA).stream()
                .mapToInt(s -> s.completadas()).sum();
        assertThat(total).isEqualTo(5);
    }
}
