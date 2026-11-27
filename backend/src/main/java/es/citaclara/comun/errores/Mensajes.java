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

    // Portal del paciente (002, contracts/api.yaml).
    public static final String EMAIL_NO_VALIDO = "Escribe un email válido, por ejemplo nombre@correo.es.";
    public static final String ENLACES_AGOTADOS =
            "Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.";
    public static final String ENLACE_NO_VALIDO = "Este enlace ya no es válido. Pide uno nuevo.";
    public static final String SIN_SESION_PORTAL = "Entra con el enlace que te enviamos por email para ver tus citas.";
    public static final String SESION_PORTAL_CADUCADA = "Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.";
    public static final String FUERA_DE_PLAZO =
            "Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.";
    public static final String CITA_PORTAL_YA_CAMBIADA = "Esta cita acaba de cambiar. Revisa su estado.";

    private Mensajes() {
    }
}
