package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Spec 005, FR-014: las mejoras que tocan otras specs se piden por el mapa y no se editan. */
class MapaSpecsTest {

    @Test
    @DisplayName("[005][FR-014][US3-AC2] cada solicitud del mapa indica spec afectada, propietario y texto propuesto")
    void solicitudesCompletas() throws Exception {
        String mapa = Files.readString(LectorInforme.RAIZ.resolve("specs/MAPA.md"));
        for (String fila : mapa.split("\n")) {
            if (fila.matches("^\\| S-\\d{3} \\|.*")) {
                String[] c = fila.split("\\|", -1);
                assertThat(c.length).isGreaterThanOrEqualTo(8);
                for (int i = 3; i <= 6; i++) {
                    assertThat(c[i].strip()).as(fila).isNotBlank();
                }
            }
        }
        assertThat(mapa).contains("| S-001 |");
    }

    @Test
    @DisplayName("[005][FR-014][US3-AC2] esta rama no modifica las specs 002, 003 ni 004")
    void sinTocarSpecsAjenas() throws Exception {
        Process p = new ProcessBuilder("git", "status", "--porcelain", "--", "specs/002*", "specs/003*", "specs/004*")
                .directory(LectorInforme.RAIZ.toFile()).redirectErrorStream(true).start();
        String salida;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            salida = String.join("\n", r.lines().toList());
        }
        assumeTrue(p.waitFor() == 0, "git no disponible");
        assertThat(salida).isEmpty();
    }
}
