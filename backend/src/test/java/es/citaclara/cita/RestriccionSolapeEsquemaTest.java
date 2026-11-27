package es.citaclara.cita;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Principio III: la garantía de no solape vive en la base de datos y cubre cualquier camino de
 * escritura. Estas inserciones van por SQL directo, sin pasar por ningún servicio.
 */
class RestriccionSolapeEsquemaTest extends PruebaIntegracionBase {

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-012] la base de datos rechaza un solape parcial insertado directamente")
    void rechazaSolapeParcial() {
        assertThatThrownBy(() -> insertarJorge("10:30", 45, "RESERVADA"))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo("23P01"));
    }

    @Test
    @DisplayName("[001][FR-012] la base de datos rechaza contención y coincidencia exacta")
    void rechazaContencionYCoincidencia() {
        assertThatThrownBy(() -> insertarJorge("09:45", 75, "RESERVADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarJorge("10:00", 45, "COMPLETADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarJorge("10:15", 15, "RESERVADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("[001][FR-012][US2-AC3] la base de datos acepta citas contiguas")
    void aceptaContiguas() {
        insertarJorge("10:45", 45, "RESERVADA");
        insertarJorge("09:15", 45, "RESERVADA");
        assertThat(citasDeJorge()).isEqualTo(3);
    }

    @Test
    @DisplayName("[001][FR-024][US2-AC4] una cita cancelada o no asistida no bloquea el hueco")
    void canceladasYNoAsistidasNoBloquean() {
        jdbc.update("UPDATE cita SET estado = 'CANCELADA'");
        insertarJorge("10:00", 45, "NO_ASISTIDA");
        insertarJorge("10:00", 45, "RESERVADA");
        assertThat(citasDeJorge()).isEqualTo(3);
    }

    @Test
    @DisplayName("[001][FR-012][US2-AC6] el mismo horario con otro profesional se acepta")
    void otroProfesionalNoSolapa() {
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "RESERVADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("[001][FR-009] la base de datos exige que el fin sea posterior al inicio")
    void finPosteriorAlInicio() {
        assertThatThrownBy(() -> insertarJorge("15:00", 0, "RESERVADA"))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo("23514"));
    }

    private void insertarJorge(String hora, int minutos, String estado) {
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, hora, minutos, 4000, estado);
    }

    private int citasDeJorge() {
        return jdbc.queryForObject("SELECT count(*) FROM cita WHERE profesional_id = ?", Integer.class, e.jorge());
    }

    private static String sqlState(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
