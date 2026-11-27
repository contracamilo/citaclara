package es.citaclara.portal;

import es.citaclara.portal.citas.ReglaCancelacionPortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaCancelacionPortalTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static Instant en(int anio, int mes, int dia, int hora, int minuto, int segundo) {
        return ZonedDateTime.of(anio, mes, dia, hora, minuto, segundo, 0, MADRID).toInstant();
    }

    @Test
    @DisplayName("[002][FR-012][SC-003] se puede cancelar hasta un segundo antes de que falten 24 horas; a las 24 horas exactas ya no")
    void limiteExacto() {
        Instant inicio = en(2026, 11, 25, 9, 45, 0); // miércoles 09:45
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 11, 24, 9, 44, 59))).isTrue();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 11, 24, 9, 45, 0))).isFalse();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 11, 25, 9, 45, 0))).isFalse();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 11, 25, 10, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("[002][FR-012] con el cambio a horario de verano el plazo son 24 horas reales, no de reloj")
    void cambioAVerano() {
        // 29/03/2026: a las 02:00 pasan a ser las 03:00. Cita el lunes 30/03 a las 10:00.
        Instant inicio = en(2026, 3, 30, 10, 0, 0);
        // 24 h reales antes son las 10:00 del domingo 29 (el día solo tiene 23 h, pero el plazo cruza la noche del 29 al 30).
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 3, 29, 9, 59, 59))).isTrue();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(inicio, en(2026, 3, 29, 10, 0, 0))).isFalse();
        // Cita el domingo del cambio a las 10:00: 24 h reales antes son las 09:00 del sábado.
        Instant domingo = en(2026, 3, 29, 10, 0, 0);
        assertThat(ReglaCancelacionPortal.dentroDePlazo(domingo, en(2026, 3, 28, 8, 59, 59))).isTrue();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(domingo, en(2026, 3, 28, 9, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("[002][FR-012] con el cambio a horario de invierno el plazo también son 24 horas reales")
    void cambioAInvierno() {
        // 25/10/2026: a las 03:00 vuelven a ser las 02:00 (el día tiene 25 h). Cita el domingo a las 10:00.
        Instant domingo = en(2026, 10, 25, 10, 0, 0);
        assertThat(ReglaCancelacionPortal.dentroDePlazo(domingo, en(2026, 10, 24, 10, 59, 59))).isTrue();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(domingo, en(2026, 10, 24, 11, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("[002][FR-012] el plazo cuenta horas naturales: una cita del lunes a las 10:00 se cancela hasta el domingo a las 09:59:59")
    void finDeSemana() {
        Instant lunes = en(2026, 11, 30, 10, 0, 0);
        assertThat(ReglaCancelacionPortal.dentroDePlazo(lunes, en(2026, 11, 29, 9, 59, 59))).isTrue();
        assertThat(ReglaCancelacionPortal.dentroDePlazo(lunes, en(2026, 11, 29, 10, 0, 0))).isFalse();
    }
}
