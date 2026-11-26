package es.citaclara.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Reloj único del producto: toda lógica de «ahora» lo recibe inyectado (research D4). */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionReloj {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
