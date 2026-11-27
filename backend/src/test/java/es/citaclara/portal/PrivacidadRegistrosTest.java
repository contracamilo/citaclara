package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Research R13: los registros nunca contienen un email, un secreto de enlace ni su hash. */
@ExtendWith(OutputCaptureExtension.class)
class PrivacidadRegistrosTest extends PruebaIntegracionBase {

    @Test
    @DisplayName("[002][FR-003][FR-011] ni el email ni el secreto del enlace aparecen en los registros, tampoco si falla el envío")
    void sinDatosPersonalesEnRegistros(CapturedOutput salida) {
        DatosPrueba.EscenarioPortal p = datos.escenarioPortal();
        long cita = datos.cita(p.e(), p.e().jorge(), p.e().sesionFisio(), p.laura(), DatosPrueba.HOY.plusDays(6),
                "10:00", 45, 4000, "RESERVADA");

        cliente().pedirEnlace("eleva", "nadie@ejemplo.es");
        ClienteApi laura = clienteEnPortal("eleva", "laura.romero@ejemplo.es");
        String secreto = buzon.secreto(buzon.ultimoEnlace("laura.romero@ejemplo.es"));
        laura.post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0));
        cliente().abrirSesionPortal("eleva", "secreto-inventado");

        buzon.fallarEnvios(true);
        assertThat(cliente().pedirEnlace("eleva", "teresa.mendez@ejemplo.es").estado()).isEqualTo(202);

        String registros = salida.getAll();
        assertThat(registros).contains("No se ha podido enviar un enlace de acceso al portal");
        assertThat(registros)
                .doesNotContain("@ejemplo.es")
                .doesNotContain(secreto)
                .doesNotContain("secreto-inventado")
                .doesNotContain("#enlace=");
    }
}
