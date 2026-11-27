package es.citaclara.recordatorio;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** En la 001 «mover» una cita es cancelarla y crear otra: la nueva es una cita distinta con su propio recordatorio. */
class CambioCitaRecordatorioTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;
    private ClienteApi paciente;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        paciente = cliente();
    }

    private String estado(long cita) {
        return jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita);
    }

    @Test
    @DisplayName("[003][FR-014][US3-AC1] tras cancelar la cita recordada desde recepción, su enlace antiguo no afecta a otra cita del paciente")
    void enlaceAntiguoNoAfectaALaNueva() {
        long antigua = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();
        String enlaceAntiguo = enlaceDe(antigua);
        jdbc.update("UPDATE cita SET estado = 'CANCELADA' WHERE id = ?", antigua); // la cancela recepción
        long nueva = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));      // misma hora, otra cita

        var consulta = paciente.get("/api/cancelacion/" + enlaceAntiguo);
        assertThat(consulta.cuerpo().path("situacion").asString()).isEqualTo("YA_CERRADA");
        assertThat(consulta.cuerpo().path("canceladaPorPaciente").asBoolean()).isFalse();
        assertThat(paciente.post("/api/cancelacion/" + enlaceAntiguo, Map.of()).estado()).isEqualTo(409);

        assertThat(estado(nueva)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[003][FR-014][US3-AC2] la cita nueva dentro de la ventana recibe su propio recordatorio, con enlace distinto y datos actuales")
    void citaNuevaDentroDeLaVentana() {
        long antigua = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();
        String enlaceAntiguo = enlaceDe(antigua);
        var correoAntiguo = contenidoFicheros();
        jdbc.update("UPDATE cita SET estado = 'CANCELADA' WHERE id = ?", antigua);
        long nueva = reservadaEn(e, e.jorge(), e.ana(), Duration.ofHours(34)); // otro profesional y otra hora

        assertThat(servicio.generar()).isEqualTo(1);

        assertThat(ficherosEml()).containsExactlyInAnyOrder("recordatorio-cita-" + antigua + ".eml", "recordatorio-cita-" + nueva + ".eml");
        assertThat(enlaceDe(nueva)).isNotEqualTo(enlaceAntiguo);
        assertThat(cuerpoEml(nueva)).contains("Profesional: Jorge").contains("Hora:        22:00");
        assertThat(contenidoFicheros()).as("el recordatorio antiguo no se reenvía ni se modifica")
                .containsAll(correoAntiguo);
    }

    @Test
    @DisplayName("[003][FR-014][US3-AC3] la cita nueva fuera de la ventana no genera recordatorio y el antiguo no se reenvía")
    void citaNuevaFueraDeLaVentana() {
        long antigua = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();
        var antes = contenidoFicheros();
        jdbc.update("UPDATE cita SET estado = 'CANCELADA' WHERE id = ?", antigua);
        reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(80)); // dentro de varios días

        assertThat(servicio.generar()).isZero();
        assertThat(contenidoFicheros()).isEqualTo(antes);
    }

    @Test
    @DisplayName("[003][FR-004][US3-AC2] un paciente con varias citas en la ventana recibe un correo por cita y cada enlace cancela solo la suya")
    void variasCitasDelMismoPaciente() {
        long primera = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        long segunda = reservadaEn(e, e.jorge(), e.ana(), Duration.ofHours(32));

        assertThat(servicio.generar()).isEqualTo(2);

        String enlacePrimera = enlaceDe(primera);
        assertThat(enlaceDe(segunda)).isNotEqualTo(enlacePrimera);
        assertThat(paciente.post("/api/cancelacion/" + enlacePrimera, Map.of()).estado()).isEqualTo(200);
        assertThat(estado(primera)).isEqualTo("CANCELADA");
        assertThat(estado(segunda)).isEqualTo("RESERVADA");
    }
}
