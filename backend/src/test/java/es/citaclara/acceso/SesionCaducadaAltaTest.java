package es.citaclara.acceso;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

class SesionCaducadaAltaTest extends PruebaIntegracionBase {

    @Test
    @DisplayName("[001][FR-002] con la sesión caducada el alta no se aplica (ni la cita ni la ficha nueva)")
    void altaConSesionCaducada() {
        Escenario e = datos.escenario();
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        reloj.avanzar(Duration.ofHours(8));

        Respuesta r = recepcion.post("/api/citas", Map.of(
                "profesionalId", e.jorge(), "servicioId", e.sesionFisio(),
                "nuevoPaciente", Map.of("nombre", "Carmen Díaz", "telefono", "699112233"),
                "fecha", MANANA.plusDays(1).toString(), "horaInicio", "10:00"));

        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_CADUCADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class)).isEqualTo(3);
    }
}
