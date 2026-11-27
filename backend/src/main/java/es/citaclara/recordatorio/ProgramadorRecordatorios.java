package es.citaclara.recordatorio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Lanza el proceso diario a la hora configurada (por defecto 09:00, Europe/Madrid; research D1).
 * Se desactiva con {@code citaclara.recordatorios.programador=false} (comando manual y pruebas).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "citaclara.recordatorios.programador", havingValue = "true", matchIfMissing = true)
public class ProgramadorRecordatorios {

    private final ServicioRecordatorios servicio;

    public ProgramadorRecordatorios(ServicioRecordatorios servicio) {
        this.servicio = servicio;
    }

    @Scheduled(cron = "${citaclara.recordatorios.cron}", zone = "Europe/Madrid")
    void ejecutarDiario() {
        servicio.generar();
    }
}
