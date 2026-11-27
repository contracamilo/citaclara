package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FalloRecordatorioTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
    }

    @Test
    @DisplayName("[003][FR-007][US1-AC6] si la carpeta de salida no se puede escribir, la cita no queda registrada y se reintenta después")
    void carpetaNoDisponible() throws IOException {
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        Files.deleteIfExists(DIRECTORIO_CORREO);
        Files.writeString(DIRECTORIO_CORREO, "esto es un fichero, no una carpeta");

        assertThat(servicio.generar()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recordatorio", Integer.class)).isZero();

        Files.delete(DIRECTORIO_CORREO);
        Files.createDirectories(DIRECTORIO_CORREO);
        assertThat(servicio.generar()).isEqualTo(1);
        assertThat(ficherosEml()).containsExactly("recordatorio-cita-" + cita + ".eml");
    }

    @Test
    @DisplayName("[003][FR-007][US1-AC6] una dirección de correo inválida no impide generar los recordatorios de las demás citas")
    void direccionInvalida() {
        long invalido = datos.paciente(e.clinica(), "Dirección Mala", "+34677777777", "esto no es un email");
        reservadaEn(e, e.maria(), invalido, Duration.ofHours(30));
        long valida = reservadaEn(e, e.jorge(), e.pedro(), Duration.ofHours(30));

        assertThat(servicio.generar()).isEqualTo(1);

        assertThat(ficherosEml()).containsExactly("recordatorio-cita-" + valida + ".eml");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recordatorio", Integer.class)).isEqualTo(1);
    }
}
