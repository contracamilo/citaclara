package es.citaclara.cita;

import es.citaclara.comun.errores.ErrorNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaquinaEstadosCitaTest {

    private static final Instant INICIO = Instant.parse("2026-11-26T09:00:00Z");
    private static final Instant ANTES = INICIO.minusSeconds(60);
    private static final Instant DESPUES = INICIO.plusSeconds(600);

    @Test
    @DisplayName("[001][FR-022][US3-AC1] una cita reservada futura solo admite cancelar")
    void futuraSoloCancelar() {
        assertThat(MaquinaEstadosCita.accionesDisponibles(EstadoCita.RESERVADA, INICIO, ANTES))
                .containsExactly(Accion.CANCELAR);
        assertThat(MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.CANCELAR, ANTES))
                .isEqualTo(EstadoCita.CANCELADA);
    }

    @Test
    @DisplayName("[001][FR-023][US3-AC4] antes del inicio no se puede completar ni marcar no asistida")
    void antesDelInicioNoCompletar() {
        assertThatThrownBy(() -> MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.COMPLETAR, ANTES))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.codigo()).isEqualTo("ACCION_NO_DISPONIBLE");
                    assertThat(e.getMessage()).isEqualTo("Esta cita aún no ha empezado: solo se puede cancelar.");
                });
        assertThatThrownBy(() -> MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.MARCAR_NO_ASISTIDA, ANTES))
                .isInstanceOf(ErrorNegocio.class);
    }

    @Test
    @DisplayName("[001][FR-023][US3-AC2][US3-AC3][US3-AC5] desde el inicio solo completar o marcar no asistida")
    void desdeElInicio() {
        assertThat(MaquinaEstadosCita.accionesDisponibles(EstadoCita.RESERVADA, INICIO, INICIO))
                .containsExactly(Accion.COMPLETAR, Accion.MARCAR_NO_ASISTIDA);
        assertThat(MaquinaEstadosCita.accionesDisponibles(EstadoCita.RESERVADA, INICIO, DESPUES))
                .containsExactly(Accion.COMPLETAR, Accion.MARCAR_NO_ASISTIDA);
        assertThat(MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.COMPLETAR, INICIO))
                .isEqualTo(EstadoCita.COMPLETADA);
        assertThat(MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.MARCAR_NO_ASISTIDA, DESPUES))
                .isEqualTo(EstadoCita.NO_ASISTIDA);
    }

    @Test
    @DisplayName("[001][FR-022][US3-AC5] una cita ya empezada no se puede cancelar")
    void empezadaNoCancelar() {
        assertThatThrownBy(() -> MaquinaEstadosCita.transicion(EstadoCita.RESERVADA, INICIO, Accion.CANCELAR, INICIO))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getMessage()).isEqualTo(
                        "Esta cita ya ha empezado: no se puede cancelar. Márcala como completada o no asistida."));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"COMPLETADA", "CANCELADA", "NO_ASISTIDA"})
    @DisplayName("[001][FR-021][US3-AC6] los estados finales no ofrecen ni admiten acciones")
    void estadosFinales(EstadoCita estado) {
        assertThat(MaquinaEstadosCita.accionesDisponibles(estado, INICIO, ANTES)).isEmpty();
        assertThat(MaquinaEstadosCita.accionesDisponibles(estado, INICIO, DESPUES)).isEmpty();
        for (Accion accion : Accion.values()) {
            assertThatThrownBy(() -> MaquinaEstadosCita.transicion(estado, INICIO, accion, DESPUES))
                    .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getMessage())
                            .isEqualTo("Esta cita ya está cerrada y no admite cambios."));
        }
    }
}
