package es.citaclara.soporte;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Ayudas para leer respuestas de GET /api/agenda en las pruebas. */
public final class Agendas {

    private Agendas() {
    }

    /** Huecos como «HH:mm-HH:mm», en orden. */
    public static List<String> huecos(JsonNode agenda) {
        List<String> huecos = new ArrayList<>();
        agenda.path("huecos").forEach(h -> huecos.add(h.path("horaInicio").asString() + "-" + h.path("horaFin").asString()));
        return huecos;
    }
}
