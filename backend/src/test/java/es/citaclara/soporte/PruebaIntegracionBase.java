package es.citaclara.soporte;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las pruebas de integración: aplicación completa en un puerto aleatorio contra
 * PostgreSQL 18 (Testcontainers), datos vacíos y reloj en {@link DatosPrueba#AHORA_POR_DEFECTO}
 * antes de cada test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConfiguracionPruebas.class)
public abstract class PruebaIntegracionBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelojControlable reloj;

    @Autowired
    protected JsonMapper json;

    @Value("${local.server.port}")
    protected int puerto;

    protected DatosPrueba datos;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE clinica RESTART IDENTITY CASCADE");
        reloj.fijar(DatosPrueba.AHORA_POR_DEFECTO);
        datos = new DatosPrueba(jdbc);
    }

    /** Cliente HTTP nuevo, con su propia cookie de sesión (como otra pestaña o dispositivo). */
    protected ClienteApi cliente() {
        return new ClienteApi(puerto, json);
    }

    /** Cliente con la sesión ya iniciada en la clínica indicada. */
    protected ClienteApi clienteConSesion(String identificador, String clave) {
        ClienteApi cliente = cliente();
        Respuesta respuesta = cliente.post("/api/clinicas/" + identificador + "/sesion",
                java.util.Map.of("clave", clave));
        if (respuesta.estado() != 204) {
            throw new IllegalStateException("No se pudo iniciar sesión: " + respuesta);
        }
        return cliente;
    }
}
