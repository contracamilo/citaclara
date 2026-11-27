package es.citaclara.soporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;

/**
 * PostgreSQL 18 real (la exclusion constraint y unaccent no existen en H2; plan, «Seguimiento de
 * complejidad») y reloj controlable. Spring cachea el contexto, así que el contenedor es único
 * para toda la suite.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionPruebas {

    public static final String IMAGEN_POSTGRES = "postgres:18.7-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGEN_POSTGRES));
    }

    @Bean
    RelojControlable relojControlable() {
        return new RelojControlable(DatosPrueba.AHORA_POR_DEFECTO.toInstant());
    }

    @Bean
    @Primary
    Clock relojPruebas(RelojControlable reloj) {
        return reloj;
    }
}
