package es.citaclara.agenda;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Huecos libres (FR-007): complemento, dentro del horario del día, de la unión de los intervalos
 * ocupados por citas reservadas o completadas. Trabaja con instantes, así que los días de cambio de
 * hora no producen huecos ni solapes fantasma (research D4).
 */
public final class CalculadoraHuecos {

    public record Intervalo(Instant inicio, Instant fin) {
    }

    /** {@code reservable} es falso si el hueco ya ha terminado por completo (FR-008). */
    public record Hueco(Instant inicio, Instant fin, boolean reservable) {
    }

    private CalculadoraHuecos() {
    }

    public static List<Hueco> calcular(Instant apertura, Instant cierre, List<Intervalo> ocupados, Instant ahora) {
        List<Intervalo> ordenados = new ArrayList<>(ocupados);
        ordenados.sort(Comparator.comparing(Intervalo::inicio));
        List<Hueco> huecos = new ArrayList<>();
        Instant cursor = apertura;
        for (Intervalo ocupado : ordenados) {
            if (ocupado.inicio().isAfter(cursor)) {
                anadir(huecos, cursor, min(ocupado.inicio(), cierre), ahora);
            }
            if (ocupado.fin().isAfter(cursor)) {
                cursor = ocupado.fin();
            }
            if (!cursor.isBefore(cierre)) {
                return huecos;
            }
        }
        anadir(huecos, cursor, cierre, ahora);
        return huecos;
    }

    private static void anadir(List<Hueco> huecos, Instant inicio, Instant fin, Instant ahora) {
        if (fin.isAfter(inicio)) {
            huecos.add(new Hueco(inicio, fin, fin.isAfter(ahora)));
        }
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
