package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SinDuplicadosTest extends PruebaRecordatoriosBase {

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
    }

    @Test
    @DisplayName("[003][FR-004][US1-AC2] una segunda ejecución el mismo día y otra al día siguiente no generan ni modifican nada")
    void segundaEjecucionNoDuplica() {
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        assertThat(servicio.generar()).isEqualTo(1);
        var antes = contenidoFicheros();

        assertThat(servicio.generar()).isZero();
        assertThat(contenidoFicheros()).isEqualTo(antes);

        reloj.avanzar(Duration.ofHours(24)); // la cita queda ahora a 6 h: fuera de la ventana
        assertThat(servicio.generar()).isZero();
        assertThat(contenidoFicheros()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recordatorio WHERE cita_id = ?", Integer.class, cita)).isEqualTo(1);
    }

    @Test
    @DisplayName("[003][FR-005][US1-AC1] el registro guarda cita, email, instante de generación y la huella del enlace del correo")
    void registroDelRecordatorio() {
        long cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT clinica_id, email_destino, enlace_huella, generado_en FROM recordatorio WHERE cita_id = ?", cita);
        assertThat(fila.get("clinica_id")).isEqualTo(e.clinica());
        assertThat(fila.get("email_destino")).isEqualTo("ana@ejemplo.es");
        assertThat(((java.sql.Timestamp) fila.get("generado_en")).toInstant()).isEqualTo(reloj.instant());
        Matcher enlace = Pattern.compile("/cancelar/([A-Za-z0-9_-]{43})").matcher(cuerpoEml(cita));
        assertThat(enlace.find()).isTrue();
        assertThat(fila.get("enlace_huella")).isEqualTo(EnlaceCancelacion.huella(enlace.group(1)))
                .as("solo se guarda la huella, nunca el enlace").isNotEqualTo(enlace.group(1));
    }

    @Test
    @DisplayName("[003][FR-004][US1-AC2] ejecuciones simultáneas dejan exactamente un recordatorio y un fichero por cita")
    void ejecucionesSimultaneas() throws Exception {
        int citas = 10;
        Instant base = reloj.instant().plus(Duration.ofHours(26));
        for (int i = 0; i < citas; i++) {
            Instant inicio = base.plus(Duration.ofHours(i));
            datos.cita(e.clinica(), e.maria(), e.sesionFisio(), e.ana(), inicio, inicio.plusSeconds(45 * 60L), 4000, "RESERVADA");
        }
        int hilos = 6;
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    return servicio.generar();
                }));
            }
            salida.countDown();
            int total = 0;
            for (Future<Integer> r : resultados) {
                total += r.get();
            }
            assertThat(total).isEqualTo(citas);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recordatorio", Integer.class)).isEqualTo(citas);
        assertThat(ficherosEml()).hasSize(citas);
    }
}
