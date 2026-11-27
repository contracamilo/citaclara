package es.citaclara.comun.errores;

import es.citaclara.soporte.CapturaRegistros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-008 y SC-005 (H-03): ni el manejador de errores deja datos personales en los registros. */
class ManejadorErroresRegistroTest {

    private static final String DETALLE = "Key (clinica_id, telefono)=(1, +34677889900) already exists.";

    @Test
    @DisplayName("[005][FR-008][SC-005][US4-AC2] un error inesperado se registra con sus clases y SQLState, sin el mensaje que lleva datos personales")
    void errorInesperadoSinMensaje() {
        SQLException causa = new SQLException("ERROR: duplicate key. Detail: " + DETALLE, "23505");
        Exception error = new RuntimeException("fallo con " + DETALLE, causa);
        try (CapturaRegistros registros = new CapturaRegistros()) {
            new ManejadorErrores().inesperado(error);
            assertThat(registros.texto()).contains("RuntimeException", "sqlState=23505");
            assertThat(registros.fugas("+34677889900", "telefono)=", "Detail")).isEmpty();
        }
    }

    @Test
    @DisplayName("[005][FR-008][SC-005][US4-AC2] una violación de integridad no prevista tampoco registra los valores en conflicto")
    void integridadSinValores() {
        SQLException causa = new SQLException("Detail: " + DETALLE, "23503");
        DataIntegrityViolationException error = new DataIntegrityViolationException("no se pudo guardar: " + DETALLE, causa);
        try (CapturaRegistros registros = new CapturaRegistros()) {
            new ManejadorErrores().integridad(error);
            assertThat(registros.texto()).contains("sqlState=23503");
            assertThat(registros.fugas("+34677889900", "telefono)=")).isEmpty();
        }
    }
}
