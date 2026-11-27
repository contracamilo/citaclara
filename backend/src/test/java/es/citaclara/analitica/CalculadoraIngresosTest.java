package es.citaclara.analitica;

import es.citaclara.analitica.AnaliticaRespuesta.Ingresos;
import es.citaclara.analitica.AnaliticaRespuesta.ServicioIngresos;
import es.citaclara.analitica.CalculadoraAnalitica.DatosClinica;
import es.citaclara.analitica.CalculadoraAnalitica.ProfesionalDato;
import es.citaclara.analitica.CalculadoraAnalitica.ServicioDato;
import es.citaclara.cita.EstadoCita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static es.citaclara.analitica.CitasPrueba.MARIA;
import static es.citaclara.analitica.CitasPrueba.PRIMERA;
import static es.citaclara.analitica.CitasPrueba.SESION;
import static es.citaclara.analitica.CitasPrueba.cita;
import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraIngresosTest {

    private static final PeriodoAnalitica PERIODO = PeriodoAnalitica.de(LocalDate.of(2026, 11, 30));
    private static final long SIN_CITAS = 12;
    private static final DatosClinica CLINICA = new DatosClinica(
            List.of(new ProfesionalDato(MARIA, "María", "Fisioterapia")),
            List.of(new ServicioDato(SESION, "Sesión fisioterapia"), new ServicioDato(PRIMERA, "Primera visita fisioterapia"),
                    new ServicioDato(SIN_CITAS, "Consulta nutrición")), 3300);

    private static ServicioIngresos servicio(Ingresos ingresos, long id) {
        return ingresos.servicios().stream().filter(s -> s.servicioId() == id).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("[004][FR-006][US2-AC3] solo las completadas suman en ingresos; reservadas, canceladas y no asistidas no")
    void soloCompletadas() {
        Ingresos ingresos = CalculadoraAnalitica.ingresos(PERIODO, List.of(
                cita(MARIA, SESION, "2026-11-02", "10:00", 45, 4000, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-03", "10:00", 45, 4000, EstadoCita.CANCELADA),
                cita(MARIA, SESION, "2026-11-04", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, SESION, "2026-11-05", "10:00", 45, 4000, EstadoCita.RESERVADA)), CLINICA);
        assertThat(servicio(ingresos, SESION).citasCompletadas()).isEqualTo(1);
        assertThat(servicio(ingresos, SESION).importeCentimos()).isEqualTo(4000);
        assertThat(ingresos.totalCentimos()).isEqualTo(4000);
    }

    @Test
    @DisplayName("[004][FR-006] el importe es el de la cita, no el precio actual del servicio")
    void importeDeLaCita() {
        Ingresos ingresos = CalculadoraAnalitica.ingresos(PERIODO, List.of(
                cita(MARIA, SESION, "2026-11-02", "10:00", 45, 3750, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-03", "10:00", 45, 4000, EstadoCita.COMPLETADA)), CLINICA);
        assertThat(servicio(ingresos, SESION).importeCentimos()).isEqualTo(7750);
    }

    @Test
    @DisplayName("[004][FR-010][US2-AC2] la suma de importes de los servicios es exactamente el total, sin diferencias de céntimos")
    void sumaExacta() {
        Ingresos ingresos = CalculadoraAnalitica.ingresos(PERIODO, List.of(
                cita(MARIA, SESION, "2026-11-02", "10:00", 45, 3333, EstadoCita.COMPLETADA),
                cita(MARIA, PRIMERA, "2026-11-03", "10:00", 60, 5001, EstadoCita.COMPLETADA),
                cita(MARIA, SESION, "2026-11-04", "10:00", 45, 1, EstadoCita.COMPLETADA)), CLINICA);
        assertThat(ingresos.servicios().stream().mapToLong(ServicioIngresos::importeCentimos).sum())
                .isEqualTo(ingresos.totalCentimos()).isEqualTo(8335);
    }

    @Test
    @DisplayName("[004][FR-006][US2-AC4] un servicio sin citas completadas aparece con 0 € y 0 citas")
    void servicioSinCitas() {
        Ingresos ingresos = CalculadoraAnalitica.ingresos(PERIODO, List.of(), CLINICA);
        assertThat(ingresos.servicios()).hasSize(3);
        assertThat(servicio(ingresos, SIN_CITAS).importeCentimos()).isZero();
        assertThat(servicio(ingresos, SIN_CITAS).citasCompletadas()).isZero();
        assertThat(ingresos.totalCentimos()).isZero();
        assertThat(ingresos.totalNoObtenidoCentimos()).isZero();
    }

    @Test
    @DisplayName("[004][FR-016][US2-AC5] los no obtenidos suman solo las no asistidas, por servicio y en total; las canceladas no suman")
    void noObtenidos() {
        Ingresos ingresos = CalculadoraAnalitica.ingresos(PERIODO, List.of(
                cita(MARIA, SESION, "2026-11-02", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, SESION, "2026-11-03", "10:00", 45, 4000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, PRIMERA, "2026-11-04", "10:00", 60, 5000, EstadoCita.NO_ASISTIDA),
                cita(MARIA, PRIMERA, "2026-11-05", "10:00", 60, 5000, EstadoCita.CANCELADA),
                cita(MARIA, PRIMERA, "2026-11-06", "10:00", 60, 5000, EstadoCita.COMPLETADA)), CLINICA);
        assertThat(servicio(ingresos, SESION).citasNoAsistidas()).isEqualTo(2);
        assertThat(servicio(ingresos, SESION).importeNoObtenidoCentimos()).isEqualTo(8000);
        assertThat(servicio(ingresos, PRIMERA).importeNoObtenidoCentimos()).isEqualTo(5000);
        assertThat(ingresos.totalNoObtenidoCentimos()).isEqualTo(13000);
        assertThat(ingresos.totalCentimos()).isEqualTo(5000);
    }
}
