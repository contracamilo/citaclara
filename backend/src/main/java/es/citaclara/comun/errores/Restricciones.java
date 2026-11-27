package es.citaclara.comun.errores;

import java.sql.SQLException;
import java.util.Locale;

/** Reconoce las violaciones de restricciones de la base de datos por SQLSTATE y nombre. */
public final class Restricciones {

    public static final String MENSAJE_HUECO_RECIEN_OCUPADO = "Ese hueco se acaba de ocupar. Elige otro hueco.";

    private static final String EXCLUSION = "23P01";
    private static final String UNICIDAD = "23505";

    private Restricciones() {
    }

    /** La restricción {@code cita_sin_solape} ha rechazado la escritura (RN1, research D2). */
    public static boolean esSolape(Throwable error) {
        return violacion(error, EXCLUSION, "cita_sin_solape");
    }

    /** El teléfono ya existe en la clínica ({@code paciente_telefono_unico}, FR-020). */
    public static boolean esTelefonoDuplicado(Throwable error) {
        return violacion(error, UNICIDAD, "paciente_telefono_unico");
    }

    /**
     * Interbloqueo (40P01). Con una exclusion constraint, dos inserciones simultáneas en conflicto
     * pueden esperarse mutuamente y PostgreSQL aborta una de ellas como interbloqueo en vez de como
     * violación. Ninguna cita solapada llega a registrarse; la transacción abortada puede reintentarse.
     */
    public static boolean esInterbloqueo(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static boolean violacion(Throwable error, String sqlState, String restriccion) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                String texto = String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT);
                return texto.contains(restriccion);
            }
        }
        return false;
    }
}
