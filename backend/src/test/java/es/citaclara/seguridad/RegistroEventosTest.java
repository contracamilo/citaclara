package es.citaclara.seguridad;

import es.citaclara.soporte.CapturaRegistros;
import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 005, FR-008 y SC-005 (hallazgo H-03): los registros del servidor no contienen datos
 * personales (nombre, teléfono, email) ni claves, ni siquiera cuando falla una restricción de la
 * base de datos, cuyo mensaje incluye los valores en conflicto.
 */
class RegistroEventosTest extends PruebaIntegracionBase {

    private static final String TELEFONO = "+34677889900";
    private static final String NOMBRE = "Rosario Quintanilla";
    private static final String EMAIL = "rosario.quintanilla@ejemplo.es";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaccion;

    private Escenario eleva;

    @BeforeEach
    void preparar() {
        eleva = datos.escenario();
    }

    private void insertarPaciente() {
        transaccion.executeWithoutResult(estado -> entityManager.createNativeQuery(
                        "INSERT INTO paciente (clinica_id, nombre, telefono, email) VALUES (?1, ?2, ?3, ?4)")
                .setParameter(1, eleva.clinica()).setParameter(2, NOMBRE)
                .setParameter(3, TELEFONO).setParameter(4, EMAIL).executeUpdate());
    }

    @Test
    @DisplayName("[005][FR-008][SC-005][US4-AC2] un teléfono duplicado rechazado por la base de datos no deja el teléfono, nombre ni email en los registros")
    void restriccionDeUnicidadSinDatosPersonales() {
        insertarPaciente();
        try (CapturaRegistros registros = new CapturaRegistros()) {
            assertThatThrownBy(this::insertarPaciente).isInstanceOf(RuntimeException.class);
            assertThat(registros.fugas(TELEFONO, NOMBRE, EMAIL, "699112233")).isEmpty();
        }
    }

    @Test
    @DisplayName("[005][FR-008][SC-005][US4-AC2] un acceso fallido, un recurso ajeno y una petición mal formada no escriben datos personales ni la clave en los registros")
    void peticionesRechazadasSinDatosPersonales() {
        datos.otraClinica();
        ClienteApi sesion = clienteConSesion("eleva", CLAVE_ELEVA);
        try (CapturaRegistros registros = new CapturaRegistros()) {
            cliente().post("/api/clinicas/eleva/sesion", Map.of("clave", "clave-secreta-erronea"));
            sesion.get("/api/agenda?profesionalId=9999999");
            sesion.postCrudo("/api/citas", "application/json", "{\"nuevoPaciente\":{\"nombre\":\"" + NOMBRE
                    + "\",\"telefono\":\"" + TELEFONO + "\"");
            Respuesta duplicado = sesion.post("/api/citas", Map.of(
                    "profesionalId", eleva.jorge(), "servicioId", eleva.sesionFisio(),
                    "nuevoPaciente", Map.of("nombre", "Ana Otra", "telefono", "612345678"),
                    "fecha", "2026-11-26", "horaInicio", "11:00"));
            assertThat(duplicado.estado()).isEqualTo(409);
            assertThat(registros.fugas("clave-secreta-erronea", NOMBRE, TELEFONO, "612345678", "Ana Pérez")).isEmpty();
        }
    }
}
