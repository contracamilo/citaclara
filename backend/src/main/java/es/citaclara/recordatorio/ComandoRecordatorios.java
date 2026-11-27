package es.citaclara.recordatorio;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Una ejecución del proceso diario y salir (servicio «recordatorios» de Docker Compose, perfil
 * «recordatorios»; research D12). Permite demostrar y probar el flujo sin esperar a las 09:00.
 */
@Component
@Profile("recordatorios")
public class ComandoRecordatorios implements ApplicationRunner {

    private final ServicioRecordatorios servicio;
    private final ApplicationContext contexto;

    public ComandoRecordatorios(ServicioRecordatorios servicio, ApplicationContext contexto) {
        this.servicio = servicio;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int codigo = 0;
        try {
            int generados = servicio.generar();
            System.out.printf("Recordatorios generados en esta ejecución: %d%n", generados);
        } catch (RuntimeException error) {
            System.err.println("No se han podido generar los recordatorios: " + error.getMessage());
            codigo = 1;
        }
        int salida = codigo;
        System.exit(SpringApplication.exit(contexto, (ExitCodeGenerator) () -> salida));
    }
}
