package es.citaclara.paciente;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.HOY;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

class PacientesTest extends PruebaIntegracionBase {

    private Escenario e;
    private ClienteApi recepcion;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    @Test
    @DisplayName("[001][FR-018] se busca por nombre sin distinguir tildes ni mayúsculas")
    void buscarPorNombre() {
        assertThat(nombres(recepcion.get("/api/pacientes?q=lucia"))).containsExactly("Lucía Gómez");
        assertThat(nombres(recepcion.get("/api/pacientes?q=PEREZ"))).containsExactly("Ana Pérez");
        assertThat(nombres(recepcion.get("/api/pacientes?q=ru"))).containsExactly("Pedro Ruiz");
    }

    @Test
    @DisplayName("[001][FR-018] se busca por teléfono escrito en cualquier formato")
    void buscarPorTelefono() {
        assertThat(nombres(recepcion.get("/api/pacientes?q=612%20345"))).containsExactly("Ana Pérez");
        assertThat(nombres(recepcion.get("/api/pacientes?q=633-33"))).containsExactly("Pedro Ruiz");
    }

    @Test
    @DisplayName("[001][FR-018] como máximo 20 resultados ordenados por nombre, y menos de 2 letras no busca")
    void limiteYOrden() {
        for (int i = 0; i < 25; i++) {
            datos.paciente(e.clinica(), String.format("Zoe %02d", 24 - i), "+346999999" + String.format("%02d", i), null);
        }
        List<String> nombres = nombres(recepcion.get("/api/pacientes?q=zoe"));
        assertThat(nombres).hasSize(20).isSorted();
        assertThat(nombres.getFirst()).isEqualTo("Zoe 00");
        Respuesta corta = recepcion.get("/api/pacientes?q=z");
        assertThat(corta.estado()).isEqualTo(400);
        assertThat(corta.codigo()).isEqualTo("DATOS_NO_VALIDOS");
    }

    @Test
    @DisplayName("[001][FR-003][FR-018] la búsqueda nunca devuelve pacientes de otra clínica")
    void aislamiento() {
        datos.otraClinica();
        assertThat(nombres(recepcion.get("/api/pacientes?q=lucia"))).containsExactly("Lucía Gómez");
        assertThat(recepcion.get("/api/pacientes?q=644").texto()).doesNotContain("Lucía Martín");
    }

    @Test
    @DisplayName("[001][FR-019][US2-AC8] al reservar se puede dar de alta un paciente nuevo y la cita queda asociada")
    void pacienteNuevo() {
        Respuesta r = reservar(nuevo("  Carmen Díaz ", "699 11 22 33", "carmen@ejemplo.es"), "10:00");

        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        assertThat(r.cuerpo().path("paciente").path("nombre").asString()).isEqualTo("Carmen Díaz");
        assertThat(r.cuerpo().path("paciente").path("telefono").asString()).isEqualTo("+34699112233");
        assertThat(jdbc.queryForObject("SELECT telefono FROM paciente WHERE nombre = 'Carmen Díaz'", String.class))
                .isEqualTo("+34699112233");
    }

    @Test
    @DisplayName("[001][FR-019] teléfono, email o nombre no válidos se rechazan campo a campo en español")
    void validacionDeCampos() {
        Respuesta r = reservar(nuevo(" ", "12345", "no-es-un-email"), "10:00");

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.codigo()).isEqualTo("DATOS_NO_VALIDOS");
        Map<String, String> errores = new HashMap<>();
        r.cuerpo().path("errores").forEach(err -> errores.put(err.path("campo").asString(), err.path("mensaje").asString()));
        assertThat(errores).containsEntry("nuevoPaciente.telefono", "Escribe un teléfono válido, por ejemplo 612 345 678.")
                .containsKeys("nuevoPaciente.nombre", "nuevoPaciente.email");
        assertThat(errores.values()).allMatch(m -> !m.isBlank() && !m.contains("must"));
        assertThat(pacientes()).isEqualTo(3);
    }

    @Test
    @DisplayName("[001][FR-020] un teléfono que ya existe en la clínica avisa y ofrece la ficha existente")
    void duplicado() {
        Respuesta r = reservar(nuevo("Ana Otra", "612-345-678", null), "10:00");

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("PACIENTE_DUPLICADO");
        assertThat(r.mensaje()).isEqualTo("Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?");
        assertThat(r.cuerpo().path("pacienteExistente").path("id").asLong()).isEqualTo(e.ana());
        assertThat(pacientes()).isEqualTo(3);
        assertThat(citas()).isZero();
    }

    @Test
    @DisplayName("[001][FR-020] el mismo teléfono en otra clínica sí se permite")
    void mismoTelefonoOtraClinica() {
        datos.otraClinica();
        ClienteApi otra = clienteConSesion("otra", "clave-otra");
        long profesional = jdbc.queryForObject("SELECT id FROM profesional WHERE nombre = 'Elena'", Long.class);
        long servicio = jdbc.queryForObject(
                "SELECT s.id FROM servicio s JOIN clinica c ON c.id = s.clinica_id WHERE c.identificador = 'otra'", Long.class);
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("profesionalId", profesional);
        cuerpo.put("servicioId", servicio);
        cuerpo.put("nuevoPaciente", nuevo("Ana en otra clínica", "612345678", null));
        cuerpo.put("fecha", MANANA.toString());
        cuerpo.put("horaInicio", "10:00");
        assertThat(otra.post("/api/citas", cuerpo).estado()).isEqualTo(201);
    }

    @Test
    @DisplayName("[001][FR-016] si la cita se rechaza, la ficha nueva tampoco se crea")
    void atomicidad() {
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "RESERVADA");

        assertThat(reservar(nuevo("Nuevo Uno", "611 000 001", null), "10:30").codigo()).isEqualTo("SOLAPE");
        assertThat(reservarEn(nuevo("Nuevo Dos", "611 000 002", null), HOY.toString(), "11:00").codigo())
                .isEqualTo("HORA_PASADA");
        assertThat(reservar(nuevo("Nuevo Tres", "611 000 003", null), "19:45").codigo()).isEqualTo("FUERA_DE_HORARIO");

        assertThat(pacientes()).isEqualTo(3);
        assertThat(citas()).isEqualTo(1);
    }

    @Test
    @DisplayName("[001][FR-009] se indica exactamente uno: paciente existente o paciente nuevo")
    void exactamenteUnPaciente() {
        Map<String, Object> ambos = cuerpoBase("10:00");
        ambos.put("pacienteId", e.ana());
        ambos.put("nuevoPaciente", nuevo("Alguien", "611 000 009", null));
        assertThat(recepcion.post("/api/citas", ambos).codigo()).isEqualTo("DATOS_NO_VALIDOS");
        assertThat(recepcion.post("/api/citas", cuerpoBase("10:00")).codigo()).isEqualTo("DATOS_NO_VALIDOS");
        assertThat(pacientes()).isEqualTo(3);
    }

    private Respuesta reservar(Map<String, Object> nuevoPaciente, String hora) {
        return reservarEn(nuevoPaciente, MANANA.toString(), hora);
    }

    private Respuesta reservarEn(Map<String, Object> nuevoPaciente, String fecha, String hora) {
        Map<String, Object> cuerpo = cuerpoBase(hora);
        cuerpo.put("fecha", fecha);
        cuerpo.put("nuevoPaciente", nuevoPaciente);
        return recepcion.post("/api/citas", cuerpo);
    }

    private Map<String, Object> cuerpoBase(String hora) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("profesionalId", e.jorge());
        cuerpo.put("servicioId", e.sesionFisio());
        cuerpo.put("fecha", MANANA.toString());
        cuerpo.put("horaInicio", hora);
        return cuerpo;
    }

    private static Map<String, Object> nuevo(String nombre, String telefono, String email) {
        Map<String, Object> p = new HashMap<>();
        p.put("nombre", nombre);
        p.put("telefono", telefono);
        p.put("email", email);
        return p;
    }

    private int pacientes() {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE clinica_id = ?", Integer.class, e.clinica());
    }

    private int citas() {
        return jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class);
    }

    private static List<String> nombres(Respuesta r) {
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        List<String> nombres = new ArrayList<>();
        for (JsonNode p : r.cuerpo()) {
            nombres.add(p.path("nombre").asString());
        }
        return nombres;
    }
}
