package es.citaclara.soporte;

import tools.jackson.databind.JsonNode;

import java.net.http.HttpHeaders;

/** Respuesta HTTP de prueba con el cuerpo ya interpretado como JSON (o nulo si no hay cuerpo). */
public record Respuesta(int estado, JsonNode cuerpo, String texto, HttpHeaders cabeceras) {

    public String codigo() {
        return cuerpo == null ? null : cuerpo.path("codigo").asString(null);
    }

    public String mensaje() {
        return cuerpo == null ? null : cuerpo.path("mensaje").asString(null);
    }

    public String tipoContenido() {
        return cabeceras.firstValue("Content-Type").orElse("");
    }

    @Override
    public String toString() {
        return estado + " " + texto;
    }
}
