package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-007 y FR-016: controles automáticos de seguridad en integración continua. */
class ControlesCiTest {

    private static String leer(String ruta) throws IOException {
        return Files.readString(LectorInforme.RAIZ.resolve(ruta));
    }

    @Test
    @DisplayName("[005][FR-007][FR-016][SC-007][US5-AC1] el CI analiza dependencias de Maven y npm y los secretos del historial completo")
    void analisisEnCi() throws IOException {
        String ci = leer(".github/workflows/ci.yml");
        assertThat(ci).contains("osv-scanner").contains("backend/pom.xml").contains("frontend/package-lock.json");
        assertThat(ci).contains("gitleaks").contains("fetch-depth: 0").contains("--config .gitleaks.toml");
        assertThat(ci).containsPattern("needs: \\[backend, frontend, seguridad\\]");
        assertThat(leer(".gitleaks.toml")).contains("useDefault = true");
    }

    @Test
    @DisplayName("[005][FR-016][SC-007][US5-AC2] todas las acciones de terceros están fijadas por SHA de 40 caracteres y Dependabot cubre los cuatro ecosistemas")
    void accionesFijadasYDependabot() throws IOException {
        Matcher m = Pattern.compile("(?m)^\\s*(?:- )?uses:\\s*(\\S+)").matcher(leer(".github/workflows/ci.yml"));
        int usos = 0;
        while (m.find()) {
            usos++;
            assertThat(m.group(1)).as("acción fijada por SHA").matches("[\\w./-]+@[0-9a-f]{40}");
        }
        assertThat(usos).isGreaterThan(0);
        String dependabot = leer(".github/dependabot.yml");
        assertThat(dependabot).contains("package-ecosystem: maven", "package-ecosystem: npm",
                "package-ecosystem: github-actions", "package-ecosystem: docker");
    }
}
