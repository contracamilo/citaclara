package es.citaclara.soporte;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Captura en memoria todo lo que el backend registra (mensaje, parámetros y excepciones con su
 * cadena de causas) para comprobar que los registros no contienen datos personales (spec 005, FR-008).
 */
public class CapturaRegistros implements AutoCloseable {

    private final Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public CapturaRegistros() {
        appender.start();
        raiz.addAppender(appender); // sin tocar los niveles: se mide lo que la aplicación registra de verdad
    }

    /** Todo el texto registrado desde que se creó la captura, incluidas las causas de las excepciones. */
    public String texto() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent evento : new ArrayList<>(appender.list)) {
            sb.append(evento.getFormattedMessage()).append('\n');
            IThrowableProxy t = evento.getThrowableProxy();
            while (t != null) {
                sb.append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
                t = t.getCause();
            }
        }
        return sb.toString();
    }

    public List<ILoggingEvent> eventos() {
        return List.copyOf(appender.list);
    }

    /** Devuelve los fragmentos prohibidos que aparecen en lo registrado (vacío si no hay ninguno). */
    public List<String> fugas(String... prohibidos) {
        String texto = texto();
        List<String> encontrados = new ArrayList<>();
        for (String p : prohibidos) {
            if (p != null && !p.isBlank() && texto.contains(p)) {
                encontrados.add(p);
            }
        }
        return encontrados;
    }

    @Override
    public void close() {
        raiz.detachAppender(appender);
        appender.stop();
    }
}
