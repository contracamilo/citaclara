package es.citaclara.portal.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor propio del envío de enlaces (research R2). Las pruebas lo vuelven síncrono
 * ({@code citaclara.correo.asincrono=false}) para que el buzón de prueba sea determinista.
 */
// Nombre explícito: es.citaclara.correo.ConfiguracionCorreo (003) tiene el mismo nombre simple.
@Configuration(value = "configuracionCorreoPortal", proxyBeanMethods = false)
@EnableAsync
public class ConfiguracionCorreo {

    public static final String EJECUTOR = "ejecutorCorreoPortal";

    @Bean(EJECUTOR)
    TaskExecutor ejecutorCorreoPortal(@Value("${citaclara.correo.asincrono:true}") boolean asincrono) {
        if (!asincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(2);
        ejecutor.setQueueCapacity(500);
        ejecutor.setThreadNamePrefix("correo-portal-");
        ejecutor.initialize();
        return ejecutor;
    }
}
