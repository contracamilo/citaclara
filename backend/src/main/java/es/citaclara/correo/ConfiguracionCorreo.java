package es.citaclara.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Properties;

/**
 * El modo se decide solo por configuración (FR-021): con {@code CORREO_SMTP_HOST} se envía por SMTP;
 * sin él, cada correo se guarda como {@code .eml} (research D2, contracts/correo.md).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionCorreo {

    @Bean
    EnviadorCorreo enviadorCorreo(
            @Value("${citaclara.correo.smtp-host:}") String host,
            @Value("${citaclara.correo.smtp-puerto:587}") int puerto,
            @Value("${citaclara.correo.smtp-usuario:}") String usuario,
            @Value("${citaclara.correo.smtp-clave:}") String clave,
            @Value("${citaclara.correo.remitente}") String remitente,
            @Value("${citaclara.correo.directorio}") String directorio) {
        if (!StringUtils.hasText(host)) {
            return new EnviadorCorreoFichero(Path.of(directorio), remitente);
        }
        JavaMailSenderImpl smtp = new JavaMailSenderImpl();
        smtp.setHost(host);
        smtp.setPort(puerto);
        smtp.setDefaultEncoding("UTF-8");
        if (StringUtils.hasText(usuario)) {
            smtp.setUsername(usuario);
            smtp.setPassword(clave);
            Properties propiedades = smtp.getJavaMailProperties();
            propiedades.put("mail.smtp.auth", "true");
            propiedades.put("mail.smtp.starttls.enable", "true");
        }
        return new EnviadorCorreoSmtp(smtp, remitente);
    }
}
