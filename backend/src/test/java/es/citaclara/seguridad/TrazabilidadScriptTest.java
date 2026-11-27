package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Spec 005, FR-017: un hueco de trazabilidad hace fallar ya el verify local. */
class TrazabilidadScriptTest {

    @Test
    @DisplayName("[005][FR-017] scripts/trazabilidad.sh termina con código 0 y sin huecos sobre la 005 y la 001")
    void trazabilidadCompleta() throws Exception {
        for (String spec : new String[]{"specs/005-security-audit", "specs/001-nucleo-agenda"}) {
            Process p = new ProcessBuilder("bash", "scripts/trazabilidad.sh", spec)
                    .directory(LectorInforme.RAIZ.toFile()).redirectErrorStream(true).start();
            String salida;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                salida = String.join("\n", r.lines().toList());
            }
            int codigo = p.waitFor();
            assumeTrue(codigo != 127, "bash no disponible");
            assertThat(salida).as(spec).doesNotContain("Sin test");
            assertThat(codigo).as(spec).isZero();
        }
    }
}
