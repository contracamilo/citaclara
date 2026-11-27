package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VentanaRecordatoriosTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
    }

    @Test
    @DisplayName("[003][FR-001][FR-002][US1-AC1] una cita reservada dentro de la ventana con paciente con email genera su recordatorio")
    void citaEnVentana() {
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));

        assertThat(servicio.generar()).isEqualTo(1);

        assertThat(ficherosEml()).containsExactly("recordatorio-cita-" + cita + ".eml");
    }

    @Test
    @DisplayName("[003][FR-002][US1-AC4] límites exactos: 24 h incluida, 24 h menos un segundo excluida, 48 h excluida")
    void limitesDeLaVentana() {
        long justoEnLimite = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(24));
        reservadaEn(e, e.jorge(), e.ana(), Duration.ofHours(24).minusSeconds(1));
        reservadaEn(e, e.lucia(), e.pedro(), Duration.ofHours(48));
        long casiCuarentaYOcho = reservadaEn(e, e.maria(), e.pedro(), Duration.ofHours(48).minusSeconds(1));

        assertThat(servicio.generar()).isEqualTo(2);

        assertThat(ficherosEml()).containsExactlyInAnyOrder(
                "recordatorio-cita-" + justoEnLimite + ".eml", "recordatorio-cita-" + casiCuarentaYOcho + ".eml");
    }

    @Test
    @DisplayName("[003][FR-003][US1-AC3] las citas canceladas, completadas y no asistidas no generan recordatorio")
    void soloReservadas() {
        Instant base = reloj.instant().plus(Duration.ofHours(26));
        for (int i = 0; i < 3; i++) {
            String estado = new String[]{"COMPLETADA", "CANCELADA", "NO_ASISTIDA"}[i];
            Instant inicio = base.plus(Duration.ofHours(i));
            datos.cita(e.clinica(), e.maria(), e.sesionFisio(), e.ana(), inicio, inicio.plusSeconds(45 * 60L), 4000, estado);
        }

        assertThat(servicio.generar()).isZero();
        assertThat(ficherosEml()).isEmpty();
    }

    @Test
    @DisplayName("[003][FR-006][US1-AC5] un paciente sin email (nulo o vacío) no genera recordatorio y no afecta a los demás")
    void pacienteSinEmail() {
        long vacio = datos.paciente(e.clinica(), "Sin Correo", "+34655555555", "");
        reservadaEn(e, e.maria(), e.luciaGomez(), Duration.ofHours(30));
        reservadaEn(e, e.jorge(), vacio, Duration.ofHours(30));
        long conEmail = reservadaEn(e, e.lucia(), e.ana(), Duration.ofHours(30));

        assertThat(servicio.generar()).isEqualTo(1);

        assertThat(ficherosEml()).containsExactly("recordatorio-cita-" + conEmail + ".eml");
    }
}
