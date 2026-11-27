package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CorreoRecordatorioTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
    }

    @Test
    @DisplayName("[003][FR-009][FR-010][US1-AC6] el .eml lleva destinatario, asunto, datos de la cita, hora de 24 h, enlace y teléfono, en español")
    void contenidoDelCorreo() throws Exception {
        // Reloj: miércoles 25/11/2026 12:00 → +30 h = jueves 26/11/2026 18:00 en Madrid.
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();

        MimeMessage correo = leerEml(cita);
        assertThat(((InternetAddress) correo.getRecipients(Message.RecipientType.TO)[0]).getAddress()).isEqualTo("ana@ejemplo.es");
        assertThat(correo.getSubject()).isEqualTo("Recordatorio de tu cita en Clínica Eleva: 26/11/2026 a las 18:00");
        assertThat(correo.getContentType()).containsIgnoringCase("UTF-8");
        assertThat(correo.getMessageID()).isNotBlank();
        String cuerpo = cuerpoEml(cita);
        assertThat(cuerpo)
                .contains("Hola, Ana Pérez:")
                .contains("Profesional: María")
                .contains("Servicio:    Sesión fisioterapia")
                .contains("jueves 26/11/2026")
                .contains("Hora:        18:00")
                .contains("cancélala hasta 24 horas antes")
                .contains(DatosPrueba.TELEFONO_CLINICA)
                .containsPattern("http://localhost:8080/cancelar/[A-Za-z0-9_-]{43}\\r?\\n");
    }

    @Test
    @DisplayName("[003][FR-011][US1-AC6] el correo no muestra importes ni contiene identificadores internos")
    void sinImporteNiIdentificadores() {
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();

        String cuerpo = cuerpoEml(cita);
        assertThat(cuerpo).doesNotContain("€").doesNotContain("40,00").doesNotContain("4000")
                .doesNotContainIgnoringCase("clinica_id").doesNotContainIgnoringCase("cita_id");
    }

    @Test
    @DisplayName("[003][FR-020][US1-AC6] cada recordatorio es un fichero independiente con nombre derivado de su cita")
    void unFicheroPorCita() {
        long a = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        long b = reservadaEn(e, e.jorge(), e.pedro(), Duration.ofHours(31));
        servicio.generar();

        assertThat(ficherosEml()).containsExactlyInAnyOrder("recordatorio-cita-" + a + ".eml", "recordatorio-cita-" + b + ".eml");
    }
}
