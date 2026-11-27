package es.citaclara.portal.correo;

/** Evento: hay que enviar un enlace de acceso (solo se publica si el email coincide con una ficha). */
public record EnlaceSolicitado(String email, String nombreClinica, String url) {

    @Override
    public String toString() {
        return "EnlaceSolicitado[oculto]"; // R13: nunca el email ni la URL en registros
    }
}
