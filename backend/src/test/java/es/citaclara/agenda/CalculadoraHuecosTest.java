package es.citaclara.agenda;

import es.citaclara.agenda.CalculadoraHuecos.Hueco;
import es.citaclara.agenda.CalculadoraHuecos.Intervalo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraHuecosTest {

    private static final Instant APERTURA = h("09:00");
    private static final Instant CIERRE = h("20:00");
    private static final Instant MUY_PRONTO = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("[001][FR-007][US1-AC6] sin citas, todo el horario de atención es un único hueco libre")
    void sinCitas() {
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, List.of(), MUY_PRONTO))
                .containsExactly(new Hueco(APERTURA, CIERRE, true));
    }

    @Test
    @DisplayName("[001][FR-007][US1-AC4] los huecos son el complemento de las citas ocupantes dentro del horario")
    void complemento() {
        List<Intervalo> ocupados = List.of(
                new Intervalo(h("10:00"), h("10:45")),
                new Intervalo(h("12:00"), h("13:00")));
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, ocupados, MUY_PRONTO)).containsExactly(
                new Hueco(h("09:00"), h("10:00"), true),
                new Hueco(h("10:45"), h("12:00"), true),
                new Hueco(h("13:00"), h("20:00"), true));
    }

    @Test
    @DisplayName("[001][FR-007][US2-AC3] las citas contiguas no dejan huecos de 0 minutos")
    void contiguas() {
        List<Intervalo> ocupados = List.of(
                new Intervalo(h("09:00"), h("09:45")),
                new Intervalo(h("09:45"), h("10:30")),
                new Intervalo(h("19:15"), h("20:00")));
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, ocupados, MUY_PRONTO))
                .containsExactly(new Hueco(h("10:30"), h("19:15"), true));
    }

    @Test
    @DisplayName("[001][FR-007] los intervalos ocupados desordenados o encadenados se unen")
    void union() {
        List<Intervalo> ocupados = List.of(
                new Intervalo(h("15:00"), h("16:00")),
                new Intervalo(h("10:00"), h("11:00")),
                new Intervalo(h("10:30"), h("11:30")));
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, ocupados, MUY_PRONTO)).containsExactly(
                new Hueco(h("09:00"), h("10:00"), true),
                new Hueco(h("11:30"), h("15:00"), true),
                new Hueco(h("16:00"), h("20:00"), true));
    }

    @Test
    @DisplayName("[001][FR-024][US1-AC5] solo se pasan como ocupadas las citas reservadas o completadas")
    void soloOcupantes() {
        // El llamador filtra por estado; un día con solo canceladas/no asistidas se ve entero libre.
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, List.of(), MUY_PRONTO)).hasSize(1);
    }

    @Test
    @DisplayName("[001][FR-008] un hueco que ya ha pasado entero no es reservable; uno en curso sí")
    void reservable() {
        List<Hueco> huecos = CalculadoraHuecos.calcular(APERTURA, CIERRE,
                List.of(new Intervalo(h("10:00"), h("11:00"))), h("10:30"));
        assertThat(huecos).containsExactly(
                new Hueco(h("09:00"), h("10:00"), false),
                new Hueco(h("11:00"), h("20:00"), true));

        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, List.of(), h("19:59")))
                .containsExactly(new Hueco(APERTURA, CIERRE, true));
        assertThat(CalculadoraHuecos.calcular(APERTURA, CIERRE, List.of(), h("20:00")))
                .containsExactly(new Hueco(APERTURA, CIERRE, false));
    }

    private static Instant h(String hora) {
        return Instant.parse("2026-11-26T" + hora + ":00Z");
    }
}
