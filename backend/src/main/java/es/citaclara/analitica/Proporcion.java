package es.citaclara.analitica;

/**
 * Cociente exacto {@code parte / total} (research D4, FR-010, FR-011). {@code decimas} es el
 * porcentaje por diez (93 = 9,3 %), redondeado half-up UNA sola vez con aritmética entera; es
 * {@code null} cuando {@code total} es 0 (la interfaz muestra «Sin datos», nunca 0 %).
 */
public record Proporcion(long parte, long total, Integer decimas) {

    public static Proporcion de(long parte, long total) {
        if (parte < 0 || total < 0) {
            throw new IllegalArgumentException("parte y total no pueden ser negativos");
        }
        if (total == 0) {
            return new Proporcion(parte, 0, null);
        }
        // round-half-up(1000 · parte / total) = floor((2000 · parte + total) / (2 · total))
        return new Proporcion(parte, total, (int) ((2000 * parte + total) / (2 * total)));
    }
}
