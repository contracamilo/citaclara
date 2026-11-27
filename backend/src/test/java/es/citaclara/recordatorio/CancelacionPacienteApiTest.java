package es.citaclara.recordatorio;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Todas las llamadas se hacen sin iniciar sesión en el panel: la autoridad es el enlace (FR-012). */
class CancelacionPacienteApiTest extends PruebaRecordatoriosBase {

    private static final String INVENTADO = "A".repeat(43);

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;
    private ClienteApi paciente;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        paciente = cliente();
    }

    /** Cita reservada, recordatorio generado y su enlace. */
    private String enlaceDeCitaEn(Duration desdeAhora) {
        long cita = reservadaEn(e, e.maria(), e.ana(), desdeAhora);
        servicio.generar();
        return enlaceDe(cita);
    }

    private String estadoDe(String enlace) {
        return jdbc.queryForObject("SELECT c.estado FROM cita c JOIN recordatorio r ON r.cita_id = c.id WHERE r.enlace_huella = ?",
                String.class, EnlaceCancelacion.huella(enlace));
    }

    @Test
    @DisplayName("[003][FR-012][US2-AC1] el GET muestra la cita como cancelable, sin sesión y sin datos personales ni importe")
    void consultaActiva() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));

        Respuesta r = paciente.get("/api/cancelacion/" + enlace);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().path("situacion").asString()).isEqualTo("ACTIVA");
        assertThat(r.cuerpo().path("canceladaPorPaciente").asBoolean()).isFalse();
        assertThat(r.cuerpo().path("clinica").path("nombre").asString()).isEqualTo("Clínica Eleva");
        assertThat(r.cuerpo().path("clinica").path("telefono").asString()).isEqualTo(DatosPrueba.TELEFONO_CLINICA);
        assertThat(r.cuerpo().path("cita").path("fecha").asString()).isEqualTo("2026-11-26");
        assertThat(r.cuerpo().path("cita").path("horaInicio").asString()).isEqualTo("18:00");
        assertThat(r.cuerpo().path("cita").path("profesional").asString()).isEqualTo("María");
        assertThat(r.cuerpo().path("cita").path("servicio").asString()).isEqualTo("Sesión fisioterapia");
        assertThat(r.texto()).doesNotContain("612345678").doesNotContain("ana@ejemplo.es").doesNotContain("Ana")
                .doesNotContainIgnoringCase("importe").doesNotContain("4000");
    }

    @Test
    @DisplayName("[003][FR-012][US2-AC3] abrir el enlace, aunque sea varias veces, no cancela la cita")
    void consultarNoCancela() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));

        for (int i = 0; i < 3; i++) {
            assertThat(paciente.get("/api/cancelacion/" + enlace).estado()).isEqualTo(200);
        }

        assertThat(estadoDe(enlace)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[003][FR-013][FR-015][US2-AC2] el POST con confirmación cancela la cita, la marca como cancelada por el paciente y se puede repetir")
    void confirmarCancela() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));

        Respuesta r = paciente.post("/api/cancelacion/" + enlace, Map.of());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().path("situacion").asString()).isEqualTo("YA_CERRADA");
        assertThat(r.cuerpo().path("canceladaPorPaciente").asBoolean()).isTrue();
        assertThat(estadoDe(enlace)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT c.cancelada_por FROM cita c JOIN recordatorio r ON r.cita_id = c.id WHERE r.enlace_huella = ?",
                String.class, EnlaceCancelacion.huella(enlace))).isEqualTo("PACIENTE");
        // Repetir la confirmación o recargar muestra el mismo resultado (US2-AC7).
        Respuesta repetida = paciente.post("/api/cancelacion/" + enlace, Map.of());
        assertThat(repetida.estado()).isEqualTo(200);
        assertThat(repetida.cuerpo().path("canceladaPorPaciente").asBoolean()).isTrue();
        assertThat(paciente.get("/api/cancelacion/" + enlace).cuerpo().path("situacion").asString()).isEqualTo("YA_CERRADA");
    }

    @Test
    @DisplayName("[003][FR-013][US2-AC5] el límite de 24 h es inclusivo: a 24 h exactas se puede cancelar y un segundo después ya no")
    void limiteDe24Horas() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(25));

        reloj.avanzar(Duration.ofHours(1)); // la cita empieza exactamente dentro de 24 h
        assertThat(paciente.get("/api/cancelacion/" + enlace).cuerpo().path("situacion").asString()).isEqualTo("ACTIVA");

        reloj.avanzar(Duration.ofSeconds(1));
        Respuesta consulta = paciente.get("/api/cancelacion/" + enlace);
        assertThat(consulta.cuerpo().path("situacion").asString()).isEqualTo("FUERA_DE_PLAZO");
        assertThat(consulta.cuerpo().path("clinica").path("telefono").asString()).isEqualTo(DatosPrueba.TELEFONO_CLINICA);

        Respuesta r = paciente.post("/api/cancelacion/" + enlace, Map.of());
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.codigo()).isEqualTo("FUERA_DE_PLAZO");
        assertThat(r.mensaje()).isEqualTo("Ya no puedes cancelar desde aquí. Llama a la clínica al " + DatosPrueba.TELEFONO_CLINICA + ".");
        assertThat(estadoDe(enlace)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[003][FR-013][US2-AC5] una cita ya iniciada nunca se puede cancelar desde el enlace")
    void citaIniciada() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));

        reloj.avanzar(Duration.ofHours(31));

        assertThat(paciente.get("/api/cancelacion/" + enlace).cuerpo().path("situacion").asString()).isEqualTo("FUERA_DE_PLAZO");
        assertThat(paciente.post("/api/cancelacion/" + enlace, Map.of()).estado()).isEqualTo(422);
        assertThat(estadoDe(enlace)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[003][FR-018][US2-AC4] una cita cancelada por la clínica o completada se muestra como ya cerrada y no se puede cancelar")
    void citaYaNoActiva() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));
        jdbc.update("UPDATE cita SET estado = 'CANCELADA' WHERE id = (SELECT cita_id FROM recordatorio WHERE enlace_huella = ?)",
                EnlaceCancelacion.huella(enlace));

        Respuesta consulta = paciente.get("/api/cancelacion/" + enlace);
        assertThat(consulta.cuerpo().path("situacion").asString()).isEqualTo("YA_CERRADA");
        assertThat(consulta.cuerpo().path("canceladaPorPaciente").asBoolean()).isFalse();

        Respuesta r = paciente.post("/api/cancelacion/" + enlace, Map.of());
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("ESTADO_YA_CAMBIADO");
        assertThat(r.mensaje()).isEqualTo("Esta cita ya no está activa. Si tienes dudas, llama a la clínica.");
        assertThat(r.cuerpo().path("cancelacion").path("situacion").asString()).isEqualTo("YA_CERRADA");
    }

    @Test
    @DisplayName("[003][FR-018][US2-AC6] un enlace inventado o manipulado responde siempre igual (404) sin revelar datos")
    void enlaceNoValido() {
        String valido = enlaceDeCitaEn(Duration.ofHours(30));
        String manipulado = valido.substring(0, 42) + (valido.charAt(42) == 'A' ? 'B' : 'A');

        Respuesta inventado = paciente.get("/api/cancelacion/" + INVENTADO);
        for (String malo : new String[]{INVENTADO, manipulado, "corto", valido + "x"}) {
            Respuesta get = paciente.get("/api/cancelacion/" + malo);
            Respuesta post = paciente.post("/api/cancelacion/" + malo, Map.of());
            for (Respuesta r : new Respuesta[]{get, post}) {
                assertThat(r.estado()).as(malo).isEqualTo(404);
                assertThat(r.codigo()).isEqualTo("NO_ENCONTRADO");
                assertThat(r.mensaje()).isEqualTo("No hemos encontrado lo que buscas.");
                assertThat(r.texto()).doesNotContain("Clínica").doesNotContain("María");
            }
        }
        assertThat(inventado.estado()).isEqualTo(404);
        assertThat(estadoDe(valido)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[003][FR-012][US2-AC1] el POST solo admite JSON, como el resto del producto")
    void soloJson() {
        String enlace = enlaceDeCitaEn(Duration.ofHours(30));

        Respuesta r = paciente.postCrudo("/api/cancelacion/" + enlace, "text/plain", "{}");

        assertThat(r.estado()).isEqualTo(415);
        assertThat(r.codigo()).isEqualTo("TIPO_NO_ADMITIDO");
        assertThat(estadoDe(enlace)).isEqualTo("RESERVADA");
    }
}
