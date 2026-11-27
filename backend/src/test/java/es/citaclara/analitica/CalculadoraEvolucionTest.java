package es.citaclara.analitica;

import es.citaclara.analitica.AnaliticaRespuesta.SemanaEvolucion;
import es.citaclara.analitica.CalculadoraAnalitica.DatosClinica;
import es.citaclara.analitica.CalculadoraAnalitica.ProfesionalDato;
import es.citaclara.analitica.CalculadoraAnalitica.ServicioDato;
import es.citaclara.cita.EstadoCita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static es.citaclara.analitica.CitasPrueba.JORGE;
import static es.citaclara.analitica.CitasPrueba.MARIA;
import static es.citaclara.analitica.CitasPrueba.SESION;
import static es.citaclara.analitica.CitasPrueba.cita;
import static org.assertj.core.api.Assertions.assertThat;

/** Hoy = lunes 30/11/2026: semana 1 = 05/10–11/10 … semana 8 = 23/11–29/11. */
class CalculadoraEvolucionTest {

    private static final PeriodoAnalitica PERIODO = PeriodoAnalitica.de(LocalDate.of(2026, 11, 30));
    private static final DatosClinica CLINICA = new DatosClinica(
            List.of(new ProfesionalDato(MARIA, "María", "Fisioterapia"), new ProfesionalDato(JORGE, "Jorge", "Fisioterapia")),
            List.of(new ServicioDato(SESION, "Sesión fisioterapia")), 3300);

    private static final List<CitaCerrada> CITAS = List.of(
            cita(MARIA, SESION, "2026-10-06", "10:00", 45, 4000, EstadoCita.COMPLETADA),   // semana 1
            cita(JORGE, SESION, "2026-10-07", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),  // semana 1
            cita(MARIA, SESION, "2026-11-24", "10:00", 45, 4000, EstadoCita.COMPLETADA),   // semana 8
            cita(MARIA, SESION, "2026-11-25", "10:00", 45, 4000, EstadoCita.CANCELADA));   // semana 8

    @Test
    @DisplayName("[004][FR-007][US3-AC1] devuelve 8 semanas de la más antigua a la más reciente con ingresos, tasa y ocupación por profesional")
    void ochoSemanas() {
        List<SemanaEvolucion> evolucion = CalculadoraAnalitica.evolucion(PERIODO, CITAS, CLINICA);
        assertThat(evolucion).hasSize(8);
        assertThat(evolucion.getFirst().semana().inicio()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(evolucion.getLast().semana().fin()).isEqualTo(LocalDate.of(2026, 11, 29));

        SemanaEvolucion primera = evolucion.getFirst();
        assertThat(primera.completadas()).isEqualTo(1);
        assertThat(primera.noAsistidas()).isEqualTo(1);
        assertThat(primera.ingresosCentimos()).isEqualTo(4000);
        assertThat(primera.tasaNoAsistencia().decimas()).isEqualTo(500);
        assertThat(primera.ocupacion()).extracting(o -> o.profesionalId()).containsExactly(MARIA, JORGE);
        assertThat(primera.ocupacion().get(0).ocupacion().parte()).isEqualTo(45);
        assertThat(primera.ocupacion().get(1).ocupacion().parte()).isZero();

        SemanaEvolucion ultima = evolucion.getLast();
        assertThat(ultima.canceladas()).isEqualTo(1);
        assertThat(ultima.tasaNoAsistencia().decimas()).isZero(); // 0 de 2: hay base, no es «Sin datos»
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC2] una semana sin citas aparece con 0 € y «Sin datos» en la tasa, sin omitirse")
    void semanaVacia() {
        SemanaEvolucion vacia = CalculadoraAnalitica.evolucion(PERIODO, CITAS, CLINICA).get(3);
        assertThat(vacia.completadas() + vacia.noAsistidas() + vacia.canceladas()).isZero();
        assertThat(vacia.ingresosCentimos()).isZero();
        assertThat(vacia.tasaNoAsistencia().decimas()).isNull();
        assertThat(vacia.ocupacion()).hasSize(2);
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC3] una cita se cuenta en una sola semana")
    void unaSolaSemana() {
        int total = CalculadoraAnalitica.evolucion(PERIODO, CITAS, CLINICA).stream()
                .mapToInt(s -> s.completadas() + s.noAsistidas() + s.canceladas()).sum();
        assertThat(total).isEqualTo(CITAS.size());
    }

    @Test
    @DisplayName("[004][FR-010] la suma de los ingresos semanales es igual al total de ingresos del periodo")
    void sumaSemanasIgualTotal() {
        long semanas = CalculadoraAnalitica.evolucion(PERIODO, CITAS, CLINICA).stream()
                .mapToLong(SemanaEvolucion::ingresosCentimos).sum();
        assertThat(semanas).isEqualTo(CalculadoraAnalitica.ingresos(PERIODO, CITAS, CLINICA).totalCentimos()).isEqualTo(8000);
    }

    @Test
    @DisplayName("[004][FR-009] la ocupación semanal usa los minutos del horario de esa semana")
    void ocupacionSemanal() {
        SemanaEvolucion ultima = CalculadoraAnalitica.evolucion(PERIODO, CITAS, CLINICA).getLast();
        assertThat(ultima.ocupacion().getFirst().ocupacion().total()).isEqualTo(3300);
        assertThat(ultima.ocupacion().getFirst().ocupacion().decimas()).isEqualTo(14); // 45/3300 = 1,36 %
    }
}
