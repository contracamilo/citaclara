package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AislamientoRecordatoriosTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    @Test
    @DisplayName("[003][FR-008][SC-006][US1-AC7] con dos clínicas en la ventana, cada correo solo contiene datos de su clínica y su enlace corresponde a su cita")
    void dosClinicas() {
        Escenario eleva = datos.escenario();
        OtraClinica otra = datos.otraClinica();
        jdbc.update("UPDATE paciente SET email = 'lucia.martin@ejemplo.es' WHERE id = ?", otra.paciente());
        long citaEleva = reservadaEn(eleva, eleva.maria(), eleva.ana(), Duration.ofHours(30));
        Instant inicio = reloj.instant().plus(Duration.ofHours(30));
        long citaOtra = datos.cita(otra.clinica(), otra.profesional(), otra.servicio(), otra.paciente(),
                inicio, inicio.plusSeconds(45 * 60L), 4000, "RESERVADA");

        assertThat(servicio.generar()).isEqualTo(2);

        String cuerpoEleva = cuerpoEml(citaEleva);
        assertThat(cuerpoEleva).contains("Clínica Eleva").contains("Ana Pérez").contains("María")
                .doesNotContain("Clínica Otra").doesNotContain("Lucía Martín").doesNotContain("Elena");
        String cuerpoOtra = cuerpoEml(citaOtra);
        assertThat(cuerpoOtra).contains("Clínica Otra").contains("Lucía Martín").contains("Elena")
                .doesNotContain("Clínica Eleva").doesNotContain("Ana Pérez");
        assertThat(citaDelEnlace(cuerpoEleva)).isEqualTo(citaEleva);
        assertThat(citaDelEnlace(cuerpoOtra)).isEqualTo(citaOtra);
    }

    private long citaDelEnlace(String cuerpo) {
        Matcher m = Pattern.compile("/cancelar/([A-Za-z0-9_-]{43})").matcher(cuerpo);
        assertThat(m.find()).isTrue();
        return jdbc.queryForObject("SELECT cita_id FROM recordatorio WHERE enlace_huella = ?", Long.class,
                EnlaceCancelacion.huella(m.group(1)));
    }
}
