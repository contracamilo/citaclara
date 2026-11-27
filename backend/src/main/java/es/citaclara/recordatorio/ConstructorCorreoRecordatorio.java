package es.citaclara.recordatorio;

import es.citaclara.cita.Cita;
import es.citaclara.clinica.Clinica;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.correo.CorreoSaliente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Asunto y cuerpo del recordatorio en español de España (contracts/correo.md, research D9). Sin
 * importe (FR-011), sin contenido clínico y sin identificadores internos dentro del texto.
 */
@Component
public class ConstructorCorreoRecordatorio {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ESPANA = Locale.of("es", "ES");

    private final String urlPublica;

    public ConstructorCorreoRecordatorio(@Value("${citaclara.url-publica}") String urlPublica) {
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    public CorreoSaliente construir(Cita cita, Clinica clinica, String enlace, Instant ahora) {
        LocalDate fecha = ZonaClinica.fechaLocal(cita.getInicio());
        String dia = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ESPANA) + " " + FECHA.format(fecha);
        String hora = ZonaClinica.formatoHora(cita.getInicio());
        String asunto = "Recordatorio de tu cita en " + clinica.getNombre() + ": " + FECHA.format(fecha) + " a las " + hora;
        String cuerpo = """
                Hola, %s:

                Te recordamos tu cita en %s.

                  Profesional: %s
                  Servicio:    %s
                  Día:         %s
                  Hora:        %s

                Si no puedes venir, cancélala hasta 24 horas antes de la cita desde este enlace:
                %s/cancelar/%s

                Así dejamos el hueco libre para otra persona. Si ya no puedes cancelar desde el enlace
                o prefieres cambiar la cita, llama a la clínica: %s.

                %s
                """.formatted(cita.getPaciente().getNombre(), clinica.getNombre(),
                cita.getProfesional().getNombre(), cita.getServicio().getNombre(), dia, hora,
                urlPublica, enlace, clinica.getTelefono(), clinica.getNombre());
        return new CorreoSaliente(cita.getPaciente().getEmail(), cita.getPaciente().getNombre(), asunto, cuerpo,
                "recordatorio-cita-" + cita.getId() + ".eml", ahora);
    }
}
