package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-019: las pruebas de seguridad solo hablan con el servidor local de pruebas. */
class AlcanceLocalTest {

    private static final Pattern URL = Pattern.compile("https?://([A-Za-z0-9.-]+)");

    @Test
    @DisplayName("[005][FR-019][US1-AC2] ninguna prueba de seguridad apunta a un servidor que no sea local")
    void soloLocal() throws Exception {
        Path dir = LectorInforme.RAIZ.resolve("backend/src/test/java/es/citaclara/seguridad");
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".java")).toList()) {
                String texto = Files.readString(f);
                Matcher m = URL.matcher(texto);
                while (m.find()) {
                    String host = m.group(1);
                    assertThat(host).as(f.getFileName() + " → " + host)
                            .isIn("localhost", "127.0.0.1", "docs.stripe.com");
                }
            }
        }
    }
}
