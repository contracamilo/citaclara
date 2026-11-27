package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;

import static es.citaclara.soporte.DatosPrueba.AHORA_POR_DEFECTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de integridad de data-model.md, comprobadas con SQL directo contra PostgreSQL real. */
class RestriccionRecordatorioEsquemaTest extends PruebaIntegracionBase {

    private static final String HUELLA_A = "a".repeat(64);
    private static final String HUELLA_B = "b".repeat(64);

    private Escenario e;
    private long cita;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        Instant inicio = AHORA_POR_DEFECTO.toInstant().plus(Duration.ofHours(30));
        cita = datos.cita(e.clinica(), e.maria(), e.sesionFisio(), e.ana(), inicio, inicio.plusSeconds(2700), 4000, "RESERVADA");
    }

    private void insertar(long clinica, long citaId, String huella) {
        jdbc.update("INSERT INTO recordatorio (clinica_id, cita_id, email_destino, enlace_huella, generado_en) "
                + "VALUES (?, ?, 'ana@ejemplo.es', ?, now())", clinica, citaId, huella);
    }

    @Test
    @DisplayName("[003][FR-004][US1-AC2] la base de datos rechaza un segundo recordatorio para la misma cita")
    void unoPorCita() {
        insertar(e.clinica(), cita, HUELLA_A);

        assertThatThrownBy(() -> insertar(e.clinica(), cita, HUELLA_B)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("[003][FR-008][US1-AC7] la base de datos rechaza un recordatorio con la clínica de otra")
    void aislamientoPorClinica() {
        OtraClinica otra = datos.otraClinica();

        assertThatThrownBy(() -> insertar(otra.clinica(), cita, HUELLA_A)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("[003][FR-015][US2-AC2] cancelada_por solo admite PACIENTE y solo en citas canceladas")
    void canceladaPor() {
        assertThatThrownBy(() -> jdbc.update("UPDATE cita SET cancelada_por = 'PACIENTE' WHERE id = ?", cita))
                .as("una cita reservada no puede figurar cancelada por el paciente")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE cita SET estado = 'CANCELADA', cancelada_por = 'CLINICA' WHERE id = ?", cita))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE cita SET estado = 'CANCELADA', cancelada_por = 'PACIENTE' WHERE id = ?", cita)).isEqualTo(1);
    }

    @Test
    @DisplayName("[003][FR-005][US1-AC2] borrar la cita borra su recordatorio")
    void borradoEnCascada() {
        insertar(e.clinica(), cita, HUELLA_A);

        jdbc.update("DELETE FROM cita WHERE id = ?", cita);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM recordatorio", Integer.class)).isZero();
    }
}
