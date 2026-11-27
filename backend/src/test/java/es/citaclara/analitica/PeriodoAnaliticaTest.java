package es.citaclara.analitica;

import es.citaclara.analitica.PeriodoAnalitica.Semana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodoAnaliticaTest {

    @Test
    @DisplayName("[004][FR-007] las 8 semanas terminan el domingo anterior a la semana en curso")
    void semanaEnCurso() {
        PeriodoAnalitica lunes = PeriodoAnalitica.de(LocalDate.of(2026, 11, 30));
        assertThat(lunes.semanas().getFirst().inicio()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(lunes.ultima().fin()).isEqualTo(LocalDate.of(2026, 11, 29));

        PeriodoAnalitica miercoles = PeriodoAnalitica.de(LocalDate.of(2026, 11, 25));
        assertThat(miercoles.ultima().inicio()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(miercoles.ultima().fin()).isEqualTo(LocalDate.of(2026, 11, 22));

        PeriodoAnalitica domingo = PeriodoAnalitica.de(LocalDate.of(2026, 11, 29));
        assertThat(domingo.ultima().inicio()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(domingo.ultima().fin()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    @DisplayName("[004][FR-007] devuelve 8 semanas consecutivas de lunes a domingo")
    void ochoSemanasConsecutivas() {
        PeriodoAnalitica periodo = PeriodoAnalitica.de(LocalDate.of(2026, 12, 3));
        assertThat(periodo.semanas()).hasSize(8);
        for (int i = 0; i < 8; i++) {
            Semana s = periodo.semanas().get(i);
            assertThat(s.inicio().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(s.fin().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            if (i > 0) {
                assertThat(s.inicio()).isEqualTo(periodo.semanas().get(i - 1).fin().plusDays(1));
            }
        }
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC3] la semana del cambio de hora de octubre (19/10–25/10/2026) dura 169 horas entre medianoches de Madrid")
    void semanaConCambioDeHora() {
        PeriodoAnalitica periodo = PeriodoAnalitica.de(LocalDate.of(2026, 11, 2));
        Semana cambio = periodo.semanas().stream().filter(s -> s.inicio().equals(LocalDate.of(2026, 10, 19))).findFirst().orElseThrow();
        assertThat(Duration.between(cambio.desde(), cambio.hasta())).isEqualTo(Duration.ofHours(169));
        // Las semanas contiguas comparten límite: ninguna cita queda fuera ni en dos semanas.
        assertThat(periodo.semanas().get(1).hasta()).isEqualTo(periodo.semanas().get(2).desde());
        assertThat(periodo.hasta()).isEqualTo(periodo.ultima().hasta());
    }
}
