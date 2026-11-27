package es.citaclara.soporte;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base de las pruebas de integración: aplicación completa en un puerto aleatorio contra
 * PostgreSQL 18 (Testcontainers), datos vacíos y reloj en {@link DatosPrueba#AHORA_POR_DEFECTO}
 * antes de cada test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "citaclara.correo.asincrono=false") // 002: envío del enlace determinista en pruebas
@Import(ConfiguracionPruebas.class)
public abstract class PruebaIntegracionBase {

    /** Carpeta de correo simulado de toda la suite (un solo contexto de Spring; 003, FR-020). */
    protected static final Path DIRECTORIO_CORREO = crearDirectorioCorreo();

    private static Path crearDirectorioCorreo() {
        try {
            return Files.createTempDirectory("salida-correo-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void propiedadesComunes(DynamicPropertyRegistry registro) {
        registro.add("citaclara.correo.directorio", DIRECTORIO_CORREO::toString);
        // El proceso diario solo se ejecuta a mano en las pruebas, con el reloj controlable.
        registro.add("citaclara.recordatorios.programador", () -> "false");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelojControlable reloj;

    @Autowired
    protected JsonMapper json;

    @Value("${local.server.port}")
    protected int puerto;

    @Autowired
    protected BuzonPrueba buzon;

    protected DatosPrueba datos;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE clinica RESTART IDENTITY CASCADE");
        reloj.fijar(DatosPrueba.AHORA_POR_DEFECTO);
        datos = new DatosPrueba(jdbc);
        buzon.vaciar();
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

    /** Cliente con la sesión del portal abierta con el enlace que recibe {@code email} (002). */
    protected ClienteApi clienteEnPortal(String identificador, String email) {
        ClienteApi cliente = cliente();
        Respuesta pedida = cliente.pedirEnlace(identificador, email);
        if (pedida.estado() != 202) {
            throw new IllegalStateException("No se pudo pedir el enlace: " + pedida);
        }
        Respuesta sesion = cliente.abrirSesionPortal(identificador, buzon.secreto(buzon.ultimoEnlace(email)));
        if (sesion.estado() != 204) {
            throw new IllegalStateException("No se pudo abrir la sesión del portal: " + sesion);
        }
        return cliente;
    }
}
