package es.citaclara.comun;

import es.citaclara.comun.errores.ErrorNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZonaClinicaTest {

    @Test
    @DisplayName("[001][FR-026] convierte hora local de Madrid a instante en invierno (UTC+1) y verano (UTC+2)")
    void conversionInviernoYVerano() {
        assertThat(ZonaClinica.aInstante(LocalDate.of(2026, 11, 26), LocalTime.of(10, 0)))
                .isEqualTo(Instant.parse("2026-11-26T09:00:00Z"));
        assertThat(ZonaClinica.aInstante(LocalDate.of(2027, 6, 15), LocalTime.of(10, 0)))
                .isEqualTo(Instant.parse("2027-06-15T08:00:00Z"));
    }

    @Test
    @DisplayName("[001][FR-026] convierte un instante a fecha y hora local de Madrid")
    void instanteALocal() {
        Instant instante = Instant.parse("2026-11-25T23:30:00Z");
        assertThat(ZonaClinica.fechaLocal(instante)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(ZonaClinica.horaLocal(instante)).isEqualTo(LocalTime.of(0, 30));
    }

    @Test
    @DisplayName("[001][FR-026] en el cambio de marzo la hora inexistente 02:30 no es válida")
    void horaInexistenteEnMarzo() {
        assertThatThrownBy(() -> ZonaClinica.aInstante(LocalDate.of(2027, 3, 28), LocalTime.of(2, 30)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.codigo()).isEqualTo("HORA_NO_VALIDA"));
    }

    @Test
    @DisplayName("[001][FR-026] en el cambio de octubre la hora ambigua 02:30 no es válida")
    void horaAmbiguaEnOctubre() {
        assertThatThrownBy(() -> ZonaClinica.aInstante(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.codigo()).isEqualTo("HORA_NO_VALIDA"));
    }

    @Test
    @DisplayName("[001][FR-026] la duración se suma sobre el instante y conserva la duración real en el cambio de hora")
    void duracionRealEnCambioDeHora() {
        Instant inicio = ZonaClinica.aInstante(LocalDate.of(2026, 10, 25), LocalTime.of(1, 30));
        Instant fin = inicio.plus(Duration.ofMinutes(90));
        assertThat(Duration.between(inicio, fin)).isEqualTo(Duration.ofMinutes(90));
        // 01:30 (UTC+2) + 90 min reales = 02:00 local tras el retraso (UTC+1).
        assertThat(ZonaClinica.horaLocal(fin)).isEqualTo(LocalTime.of(2, 0));
    }

    @Test
    @DisplayName("[001][FR-026][US1-AC3] «hoy» es la fecha de Madrid según el reloj, no la UTC")
    void hoyEnMadrid() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-25T23:30:00Z"), ZoneOffset.UTC);
        assertThat(ZonaClinica.hoy(reloj)).isEqualTo(LocalDate.of(2026, 11, 26));
    }
}
