package es.citaclara.semilla;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Carga de los datos de demostración con un comando explícito (servicio «semilla» de Docker
 * Compose, perfil «semilla»), nunca al arrancar el servidor (research D13).
 */
@Component
@Profile("semilla")
public class ComandoSemilla implements ApplicationRunner {

    private final CargadorSemilla cargador;
    private final ApplicationContext contexto;
    private final Environment entorno;

    public ComandoSemilla(CargadorSemilla cargador, ApplicationContext contexto, Environment entorno) {
        this.cargador = cargador;
        this.entorno = entorno;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int codigo = 0;
        if (entorno.acceptsProfiles(org.springframework.core.env.Profiles.of("produccion"))) {
            System.err.println("La carga de datos de demostración no se puede ejecutar con el perfil de producción.");
            System.exit(SpringApplication.exit(contexto, (ExitCodeGenerator) () -> 1));
            return;
        }
        try {
            CargadorSemilla.Resumen r = cargador.cargar();
            System.out.printf("""
                    Datos de demostración cargados: %s.
                      Día de carga: %s (no recibe citas). Clave del panel: %s
                      Historia (días laborables −40 a −1): %d citas
                      Futuro (días laborables +1 a +10): %d citas
                      Por estado: %s
                      Recuentos documentados en specs/001-nucleo-agenda/datos-demostracion.md
                    """, CatalogoDemostracion.NOMBRE, r.diaDeCarga(), CatalogoDemostracion.CLAVE,
                    r.historia(), r.futuro(), r.porEstado());
        } catch (RuntimeException error) {
            System.err.println("No se han podido cargar los datos de demostración: " + error.getMessage());
            codigo = 1;
        }
        int salida = codigo;
        System.exit(SpringApplication.exit(contexto, (ExitCodeGenerator) () -> salida));
    }
}
