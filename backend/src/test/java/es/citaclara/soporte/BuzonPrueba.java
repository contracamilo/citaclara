package es.citaclara.soporte;

import es.citaclara.portal.correo.CorreoPortal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Buzón en memoria que sustituye al envío SMTP del portal en las pruebas (002, research R2). Con
 * {@link #fallarEnvios(boolean)} simula un servidor de correo caído (T066).
 */
public class BuzonPrueba implements CorreoPortal {

    public record Correo(String email, String clinica, String url) {
    }

    private final List<Correo> enviados = new CopyOnWriteArrayList<>();
    private volatile boolean fallar;

    @Override
    public void enviarEnlace(String email, String nombreClinica, String url) {
        if (fallar) {
            throw new IllegalStateException("SMTP caído al enviar a " + email + " " + url);
        }
        enviados.add(new Correo(email, nombreClinica, url));
    }

    public List<Correo> enviados() {
        return List.copyOf(enviados);
    }

    public List<Correo> enviadosA(String email) {
        return enviados.stream().filter(c -> c.email().equals(email)).toList();
    }

    public String ultimoEnlace(String email) {
        List<Correo> correos = enviadosA(email);
        if (correos.isEmpty()) {
            throw new IllegalStateException("No se ha enviado ningún enlace a " + email);
        }
        return correos.getLast().url();
    }

    /** Lo que va tras {@code #enlace=} en la URL del enlace. */
    public String secreto(String url) {
        int posicion = url.indexOf("#enlace=");
        if (posicion < 0) {
            throw new IllegalStateException("URL de enlace sin secreto: " + url);
        }
        return url.substring(posicion + "#enlace=".length());
    }

    public void fallarEnvios(boolean fallar) {
        this.fallar = fallar;
    }

    public void vaciar() {
        enviados.clear();
        fallar = false;
    }
}
