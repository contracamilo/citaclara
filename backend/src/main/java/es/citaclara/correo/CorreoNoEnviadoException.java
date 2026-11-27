package es.citaclara.correo;

/** El correo no se ha podido guardar o enviar; quien lo pidió no debe darlo por enviado (FR-007). */
public class CorreoNoEnviadoException extends RuntimeException {

    public CorreoNoEnviadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
