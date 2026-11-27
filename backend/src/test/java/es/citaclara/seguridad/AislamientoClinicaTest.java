package es.citaclara.seguridad;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 005, FR-003 y SC-006: desde la sesión de una clínica no se llega a nada de otra por ninguna
 * vía y el rechazo es idéntico al de un recurso inexistente (para no revelar qué existe).
 */
class AislamientoClinicaTest extends PruebaIntegracionBase {

    private static final long INEXISTENTE = 9_999_999L;

    private Escenario eleva;
    private OtraClinica otra;
    private long citaAjena;
    private ClienteApi sesion;

    @BeforeEach
    void preparar() {
        eleva = datos.escenario();
        otra = datos.otraClinica();
        Instant inicio = DatosPrueba.instante(DatosPrueba.MANANA, "10:00");
        citaAjena = datos.cita(otra.clinica(), otra.profesional(), otra.servicio(), otra.paciente(),
                inicio, inicio.plusSeconds(45 * 60), 4000, "RESERVADA");
        sesion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    private static void igualQueInexistente(Respuesta ajeno, Respuesta inexistente) {
        assertThat(ajeno.estado()).isEqualTo(inexistente.estado());
        assertThat(ajeno.codigo()).isEqualTo(inexistente.codigo());
        assertThat(ajeno.mensaje()).isEqualTo(inexistente.mensaje());
    }

    private Map<String, Object> nuevaCita(long profesional, long servicio, long paciente) {
        return Map.of("profesionalId", profesional, "servicioId", servicio, "pacienteId", paciente,
                "fecha", DatosPrueba.MANANA.toString(), "horaInicio", "16:00");
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] la agenda de un profesional de otra clínica responde como un profesional inexistente")
    void agendaAjena() {
        Respuesta ajeno = sesion.get("/api/agenda?profesionalId=" + otra.profesional() + "&fecha=" + DatosPrueba.MANANA);
        Respuesta inexistente = sesion.get("/api/agenda?profesionalId=" + INEXISTENTE + "&fecha=" + DatosPrueba.MANANA);
        assertThat(ajeno.estado()).isEqualTo(404);
        igualQueInexistente(ajeno, inexistente);
        assertThat(ajeno.texto()).doesNotContain("Elena", "Lucía Martín");
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] reservar con profesional, servicio o paciente de otra clínica responde como inexistente")
    void altaConRecursosAjenos() {
        Respuesta profesionalAjeno = sesion.post("/api/citas", nuevaCita(otra.profesional(), eleva.sesionFisio(), eleva.ana()));
        Respuesta profesionalInexistente = sesion.post("/api/citas", nuevaCita(INEXISTENTE, eleva.sesionFisio(), eleva.ana()));
        assertThat(profesionalAjeno.estado()).isEqualTo(404);
        igualQueInexistente(profesionalAjeno, profesionalInexistente);

        Respuesta servicioAjeno = sesion.post("/api/citas", nuevaCita(eleva.maria(), otra.servicio(), eleva.ana()));
        Respuesta servicioInexistente = sesion.post("/api/citas", nuevaCita(eleva.maria(), INEXISTENTE, eleva.ana()));
        assertThat(servicioAjeno.estado()).isEqualTo(404);
        igualQueInexistente(servicioAjeno, servicioInexistente);

        Respuesta pacienteAjeno = sesion.post("/api/citas", nuevaCita(eleva.maria(), eleva.sesionFisio(), otra.paciente()));
        Respuesta pacienteInexistente = sesion.post("/api/citas", nuevaCita(eleva.maria(), eleva.sesionFisio(), INEXISTENTE));
        assertThat(pacienteAjeno.estado()).isEqualTo(404);
        igualQueInexistente(pacienteAjeno, pacienteInexistente);

        Integer citasAjenas = jdbc.queryForObject("SELECT count(*) FROM cita WHERE clinica_id = ?", Integer.class, otra.clinica());
        assertThat(citasAjenas).isEqualTo(1);
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] cambiar el estado de una cita de otra clínica responde como cita inexistente y no la toca")
    void estadoDeCitaAjena() {
        Map<String, Object> cuerpo = Map.of("accion", "CANCELAR", "version", 0);
        Respuesta ajeno = sesion.post("/api/citas/" + citaAjena + "/estado", cuerpo);
        Respuesta inexistente = sesion.post("/api/citas/" + INEXISTENTE + "/estado", cuerpo);
        assertThat(ajeno.estado()).isEqualTo(404);
        igualQueInexistente(ajeno, inexistente);
        String estado = jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, citaAjena);
        assertThat(estado).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] la búsqueda de pacientes solo devuelve fichas de la propia clínica")
    void busquedaDePacientes() {
        Respuesta r = sesion.get("/api/pacientes?q=" + "Lucía");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.texto()).contains("Lucía Gómez").doesNotContain("Lucía Martín", "+34644444444");
        Respuesta porTelefono = sesion.get("/api/pacientes?q=644444444");
        assertThat(porTelefono.estado()).isEqualTo(200);
        assertThat(porTelefono.cuerpo().size()).isZero();
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] la pantalla de acceso solo revela el nombre de la clínica, nunca sus datos")
    void datosPublicosMinimos() {
        Respuesta r = cliente().get("/api/clinicas/otra");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().propertyNames()).containsExactly("nombre");
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] ningún endpoint del panel responde sin sesión")
    void sinSesionNadaResponde() {
        ClienteApi anonimo = cliente();
        List<Respuesta> respuestas = List.of(
                anonimo.get("/api/sesion"),
                anonimo.get("/api/agenda?profesionalId=" + eleva.maria()),
                anonimo.get("/api/pacientes?q=Ana"),
                anonimo.post("/api/citas", nuevaCita(eleva.maria(), eleva.sesionFisio(), eleva.ana())),
                anonimo.post("/api/citas/" + citaAjena + "/estado", Map.of("accion", "CANCELAR", "version", 0)));
        for (Respuesta r : respuestas) {
            assertThat(r.estado()).as(r.toString()).isEqualTo(401);
            assertThat(r.texto()).doesNotContain("María", "Ana Pérez", "Elena");
        }
    }

    @Test
    @DisplayName("[005][FR-003][SC-006] con la sesión de la otra clínica, los recursos de Eleva tampoco son visibles")
    void simetria() {
        ClienteApi enOtra = clienteConSesion("otra", DatosPrueba.CLAVE_OTRA);
        Respuesta r = enOtra.get("/api/agenda?profesionalId=" + eleva.maria() + "&fecha=" + DatosPrueba.MANANA);
        assertThat(r.estado()).isEqualTo(404);
        assertThat(enOtra.get("/api/pacientes?q=Ana").cuerpo().size()).isZero();
    }
}
