package es.citaclara.semilla;

import es.citaclara.cita.EstadoCita;
import es.citaclara.semilla.CatalogoDemostracion.ServicioDemo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Generador determinista y puro (sin base de datos ni reloj) de las citas de demostración
 * (FR-029–FR-031, research D13). {@link Random} con semilla fija: su algoritmo lo fija la
 * especificación de Java, así que la secuencia es idéntica en cualquier JVM. La secuencia solo
 * depende de las posiciones relativas, nunca del día de la semana de la carga.
 */
public final class GeneradorSemilla {

    public static final long SEMILLA = 20261126L;

    static final double PROPORCION_NO_ASISTIDAS = 0.10;
    static final double PROPORCION_CANCELADAS = 0.08;

    private GeneradorSemilla() {
    }

    public static List<CitaSemilla> generar() {
        Random azar = new Random(SEMILLA);
        List<CitaSemilla> historia = new ArrayList<>();
        List<CitaSemilla> futuro = new ArrayList<>();
        for (int posicion : DiasLaborables.POSICIONES) {
            boolean esFuturo = posicion > 0;
            for (int profesional = 0; profesional < CatalogoDemostracion.PROFESIONALES.size(); profesional++) {
                rellenarDia(azar, posicion, profesional, esFuturo, esFuturo ? futuro : historia);
            }
        }
        asignarCuotasDeLaHistoria(azar, historia);
        List<CitaSemilla> todas = new ArrayList<>(historia);
        todas.addAll(futuro);
        return List.copyOf(todas);
    }

    /**
     * Desde las 09:00 encadena huecos de 0–60 min (múltiplos de 15; en el futuro, 30–90 min para
     * dejar huecos libres) y servicios de la especialidad (≈ 20 % primeras visitas) mientras la cita
     * termine a las 20:00 como muy tarde. Las citas de un profesional nunca se solapan.
     */
    private static void rellenarDia(Random azar, int posicion, int profesional, boolean esFuturo, List<CitaSemilla> destino) {
        String especialidad = CatalogoDemostracion.PROFESIONALES.get(profesional).especialidad();
        List<Integer> servicios = IntStream.range(0, CatalogoDemostracion.SERVICIOS.size())
                .filter(i -> CatalogoDemostracion.SERVICIOS.get(i).especialidad().equals(especialidad)).boxed().toList();
        int habitual = servicios.stream().filter(i -> !CatalogoDemostracion.SERVICIOS.get(i).primeraVisita()).findFirst().orElseThrow();
        int primera = servicios.stream().filter(i -> CatalogoDemostracion.SERVICIOS.get(i).primeraVisita()).findFirst().orElseThrow();

        LocalTime hora = CatalogoDemostracion.APERTURA;
        while (true) {
            int hueco = esFuturo ? (2 + azar.nextInt(5)) * 15 : azar.nextInt(5) * 15;
            int servicio = azar.nextInt(5) == 0 ? primera : habitual;
            int paciente = azar.nextInt(CatalogoDemostracion.PACIENTES.size());
            ServicioDemo datos = CatalogoDemostracion.SERVICIOS.get(servicio);
            int inicioMin = hora.toSecondOfDay() / 60 + hueco;
            int finMin = inicioMin + datos.minutos();
            if (finMin > CatalogoDemostracion.CIERRE.toSecondOfDay() / 60) {
                return;
            }
            LocalTime inicio = LocalTime.ofSecondOfDay(inicioMin * 60L);
            destino.add(new CitaSemilla(posicion, profesional, inicio, servicio, paciente,
                    esFuturo ? EstadoCita.RESERVADA : EstadoCita.COMPLETADA));
            hora = LocalTime.ofSecondOfDay(finMin * 60L);
        }
    }

    /** Cuota exacta, no probabilidad: round(10 %) no asistidas y round(8 %) canceladas (SC-008). */
    private static void asignarCuotasDeLaHistoria(Random azar, List<CitaSemilla> historia) {
        int total = historia.size();
        int noAsistidas = (int) Math.round(PROPORCION_NO_ASISTIDAS * total);
        int canceladas = (int) Math.round(PROPORCION_CANCELADAS * total);
        List<Integer> indices = new ArrayList<>(IntStream.range(0, total).boxed().toList());
        Collections.shuffle(indices, azar);
        for (int i = 0; i < noAsistidas + canceladas; i++) {
            int indice = indices.get(i);
            historia.set(indice, historia.get(indice).conEstado(i < noAsistidas ? EstadoCita.NO_ASISTIDA : EstadoCita.CANCELADA));
        }
    }
}
