package es.citaclara.comun.errores;

/** Textos visibles comunes (contracts/api.yaml y contracts/ui.md). */
public final class Mensajes {

    public static final String NO_ENCONTRADO = "No hemos encontrado lo que buscas.";
    public static final String DATOS_NO_VALIDOS = "Revisa los datos marcados e inténtalo de nuevo.";
    public static final String TIPO_NO_ADMITIDO =
            "No se ha podido enviar la petición. Recarga la página e inténtalo de nuevo.";
    public static final String ERROR_INTERNO = "Algo ha fallado. Inténtalo de nuevo en unos segundos.";
    public static final String SIN_SESION = "Introduce la clave de la clínica para entrar.";
    public static final String SESION_CADUCADA = "Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.";
    public static final String CLAVE_INCORRECTA = "La clave no es correcta. Revísala e inténtalo de nuevo.";
    public static final String HORA_NO_VALIDA = "Esa hora no es válida. Elige una hora en pasos de 5 minutos.";
    public static final String HORA_INEXISTENTE =
            "Esa hora no existe ese día por el cambio de horario. Elige otra hora.";

    private Mensajes() {
    }
}
