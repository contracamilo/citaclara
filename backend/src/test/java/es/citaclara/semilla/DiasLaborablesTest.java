package es.citaclara.semilla;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiasLaborablesTest {

    private static final LocalDate MIERCOLES = LocalDate.of(2026, 11, 25);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDate SABADO = LocalDate.of(2026, 11, 28);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 29);

    @Test
    @DisplayName("[001][FR-029] desde un miércoles, el día laborable −1 es el martes y el +1 el jueves")
    void desdeMiercoles() {
        assertThat(DiasLaborables.fecha(MIERCOLES, -1)).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(DiasLaborables.fecha(MIERCOLES, 1)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(DiasLaborables.fecha(MIERCOLES, 3)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("[001][FR-029] desde un lunes, el −1 es el viernes anterior y el +1 el martes")
    void desdeLunes() {
        assertThat(DiasLaborables.fecha(LUNES, -1)).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(DiasLaborables.fecha(LUNES, 1)).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("[001][FR-029] desde un sábado o domingo, el −1 es el viernes previo y el +1 el lunes siguiente")
    void desdeFinDeSemana() {
        for (LocalDate carga : new LocalDate[]{SABADO, DOMINGO}) {
            assertThat(DiasLaborables.fecha(carga, -1)).isEqualTo(LocalDate.of(2026, 11, 27));
            assertThat(DiasLaborables.fecha(carga, 1)).isEqualTo(LocalDate.of(2026, 11, 30));
        }
    }

    @Test
    @DisplayName("[001][FR-029][FR-031] 40 posiciones hacia atrás y 10 hacia delante, siempre de lunes a viernes y sin el día de carga")
    void rangoCompleto() {
        for (LocalDate carga = LUNES; carga.isBefore(LUNES.plusDays(7)); carga = carga.plusDays(1)) {
            for (int posicion : DiasLaborables.POSICIONES) {
                LocalDate fecha = DiasLaborables.fecha(carga, posicion);
                assertThat(fecha.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                assertThat(fecha).isNotEqualTo(carga);
                assertThat(fecha.isBefore(carga)).isEqualTo(posicion < 0);
            }
            if (carga.getDayOfWeek().getValue() <= 5) {
                // ≈ 8 semanas hacia atrás y ≈ 2 hacia delante.
                assertThat(DiasLaborables.fecha(carga, -40)).isEqualTo(carga.minusWeeks(8));
                assertThat(DiasLaborables.fecha(carga, 10)).isEqualTo(carga.plusWeeks(2));
            }
        }
        assertThat(DiasLaborables.POSICIONES).hasSize(50).doesNotContain(0);
        // Sin festivos: el 8 de diciembre (martes) es un día laborable más.
        assertThat(DiasLaborables.fecha(LocalDate.of(2026, 12, 7), 1)).isEqualTo(LocalDate.of(2026, 12, 8));
    }

    @Test
    @DisplayName("[001][FR-029] la posición 0 no existe: el día de carga no recibe citas")
    void posicionCero() {
        assertThatThrownBy(() -> DiasLaborables.fecha(MIERCOLES, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
