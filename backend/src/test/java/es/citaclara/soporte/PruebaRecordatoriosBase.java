package es.citaclara.soporte;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/** Base de las pruebas de recordatorios (003): carpeta de correo vacía y utilidades de lectura de {@code .eml}. */
public abstract class PruebaRecordatoriosBase extends PruebaIntegracionBase {

    @BeforeEach
    void vaciarCorreo() throws IOException {
        restaurarDirectorio();
        try (Stream<Path> ficheros = Files.list(DIRECTORIO_CORREO)) {
            for (Path fichero : ficheros.toList()) {
                Files.deleteIfExists(fichero);
            }
        }
    }

    /** Si una prueba sustituyó la carpeta por un fichero (para provocar un fallo), la restaura. */
    private void restaurarDirectorio() throws IOException {
        if (!Files.isDirectory(DIRECTORIO_CORREO)) {
            Files.deleteIfExists(DIRECTORIO_CORREO);
            Files.createDirectories(DIRECTORIO_CORREO);
        }
    }

    /** Nombres de los {@code .eml} generados, ordenados. */
    protected List<String> ficherosEml() {
        try (Stream<Path> ficheros = Files.list(DIRECTORIO_CORREO)) {
            return ficheros.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".eml")).sorted(Comparator.naturalOrder()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Path rutaEml(long citaId) {
        return DIRECTORIO_CORREO.resolve("recordatorio-cita-" + citaId + ".eml");
    }

    protected MimeMessage leerEml(long citaId) {
        try (InputStream entrada = Files.newInputStream(rutaEml(citaId))) {
            return new MimeMessage(Session.getInstance(new Properties()), entrada);
        } catch (Exception e) {
            throw new IllegalStateException("No se puede leer el .eml de la cita " + citaId, e);
        }
    }

    protected String cuerpoEml(long citaId) {
        try {
            return (String) leerEml(citaId).getContent();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ficheros con el contenido exacto, para comprobar que un reintento no los modifica. */
    protected List<String> contenidoFicheros() {
        return ficherosEml().stream().map(n -> {
            try {
                return n + "\n" + Files.readString(DIRECTORIO_CORREO.resolve(n));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();
    }

    /** Enlace de cancelación de un recordatorio ya generado, leído del cuerpo del .eml. */
    protected String enlaceDe(long citaId) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/cancelar/([A-Za-z0-9_-]{43})").matcher(cuerpoEml(citaId));
        if (!m.find()) {
            throw new IllegalStateException("El .eml de la cita " + citaId + " no contiene enlace");
        }
        return m.group(1);
    }

    /** Cita reservada de 45 min de «Sesión fisioterapia» que empieza {@code desdeAhora} después del reloj. */
    protected long reservadaEn(DatosPrueba.Escenario e, long profesional, long paciente, Duration desdeAhora) {
        Instant inicio = reloj.instant().plus(desdeAhora);
        return datos.cita(e.clinica(), profesional, e.sesionFisio(), paciente, inicio,
                inicio.plusSeconds(45 * 60L), 4000, "RESERVADA");
    }
}
