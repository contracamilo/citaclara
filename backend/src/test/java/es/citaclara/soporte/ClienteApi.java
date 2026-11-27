package es.citaclara.soporte;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP real contra la aplicación levantada (incluye cookies de sesión). Cada instancia es
 * una «pestaña» o dispositivo distinto. Es seguro usar una instancia desde varios hilos.
 */
public class ClienteApi {

    private final int puerto;
    private final JsonMapper json;
    private final HttpClient http;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    public ClienteApi(int puerto, JsonMapper json) {
        this.puerto = puerto;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CookieManager cookies() {
        return cookies;
    }

    public Respuesta get(String ruta) {
        return enviar(peticion(ruta).GET().build());
    }

    public Respuesta delete(String ruta) {
        return enviar(peticion(ruta).DELETE().build());
    }

    public Respuesta post(String ruta, Object cuerpo) {
        String texto = json.writeValueAsString(cuerpo);
        return enviar(peticion(ruta)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(texto))
                .build());
    }

    public Respuesta postCrudo(String ruta, String tipoContenido, String cuerpo) {
        return enviar(peticion(ruta)
                .header("Content-Type", tipoContenido)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build());
    }

    /** 002: pide el enlace de acceso al portal. */
    public Respuesta pedirEnlace(String identificador, String email) {
        return post("/api/portal/" + identificador + "/enlaces", java.util.Map.of("email", email));
    }

    /** 002: abre la sesión del portal con el secreto del enlace. */
    public Respuesta abrirSesionPortal(String identificador, String secreto) {
        return post("/api/portal/" + identificador + "/sesion", java.util.Map.of("enlace", secreto));
    }

    private HttpRequest.Builder peticion(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json");
    }

    private Respuesta enviar(HttpRequest peticion) {
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            String texto = respuesta.body();
            JsonNode cuerpo = (texto == null || texto.isBlank()) ? null : json.readTree(texto);
            return new Respuesta(respuesta.statusCode(), cuerpo, texto, respuesta.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
