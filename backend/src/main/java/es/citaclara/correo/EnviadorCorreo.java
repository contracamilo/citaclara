package es.citaclara.correo;

/** Envío de correo con dos implementaciones: fichero {@code .eml} (simulado) y SMTP (research D2). */
public interface EnviadorCorreo {

    /** @throws CorreoNoEnviadoException si el correo no se ha podido entregar o guardar */
    void enviar(CorreoSaliente correo);
}
