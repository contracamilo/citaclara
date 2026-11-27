package es.citaclara.analitica;

import es.citaclara.cita.EstadoCita;
import es.citaclara.soporte.DatosPrueba;

import java.time.Instant;
import java.time.LocalDate;

/** Constructores de filas de prueba para la calculadora (sin base de datos). */
final class CitasPrueba {

    static final long MARIA = 1;
    static final long JORGE = 2;
    static final long SESION = 10;
    static final long PRIMERA = 11;

    private CitasPrueba() {
    }

    static CitaCerrada cita(long profesional, long servicio, String fecha, String hora, int minutos, int importe, EstadoCita estado) {
        Instant inicio = DatosPrueba.instante(LocalDate.parse(fecha), hora);
        return new CitaCerrada(profesional, servicio, inicio, inicio.plusSeconds(minutos * 60L), importe, estado);
    }

    static CitaCerrada enInstante(long profesional, long servicio, Instant inicio, int minutos, int importe, EstadoCita estado) {
        return new CitaCerrada(profesional, servicio, inicio, inicio.plusSeconds(minutos * 60L), importe, estado);
    }
}
