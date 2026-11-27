package es.citaclara.soporte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Constructor de datos de prueba por SQL directo (independiente de los servicios que se prueban).
 */
public class DatosPrueba {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Miércoles 25/11/2026 a las 12:00 en Madrid. */
    public static final ZonedDateTime AHORA_POR_DEFECTO = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, MADRID);

    public static final LocalDate HOY = AHORA_POR_DEFECTO.toLocalDate();
    /** Jueves 26/11/2026: un día laborable futuro. */
    public static final LocalDate MANANA = HOY.plusDays(1);

    /** Teléfono de las clínicas de prueba (003, FR-009). */
    public static final String TELEFONO_CLINICA = "+34 910 555 010";

    public static final String CLAVE_ELEVA = "clave-eleva";
    public static final String CLAVE_OTRA = "clave-otra";

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

    private final JdbcTemplate jdbc;

    public DatosPrueba(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Clínica con horario de lunes a viernes de 09:00 a 20:00. */
    public long clinica(String identificador, String nombre, String clave) {
        long id = jdbc.queryForObject(
                "INSERT INTO clinica (identificador, nombre, clave_hash, telefono) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, identificador, nombre, BCRYPT.encode(clave), TELEFONO_CLINICA);
        for (int dia = 1; dia <= 5; dia++) {
            horario(id, dia, LocalTime.of(9, 0), LocalTime.of(20, 0));
        }
        return id;
    }

    public void horario(long clinicaId, int diaSemana, LocalTime apertura, LocalTime cierre) {
        jdbc.update("INSERT INTO horario_atencion (clinica_id, dia_semana, apertura, cierre) VALUES (?, ?, ?, ?)",
                clinicaId, diaSemana, apertura, cierre);
    }

    public long profesional(long clinicaId, String nombre, String especialidad) {
        return jdbc.queryForObject(
                "INSERT INTO profesional (clinica_id, nombre, especialidad) VALUES (?, ?, ?) RETURNING id",
                Long.class, clinicaId, nombre, especialidad);
    }

    public long servicio(long clinicaId, String nombre, String especialidad, int minutos, int precioCentimos) {
        return jdbc.queryForObject(
                "INSERT INTO servicio (clinica_id, nombre, especialidad, duracion_minutos, precio_centimos) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, clinicaId, nombre, especialidad, minutos, precioCentimos);
    }

    public long paciente(long clinicaId, String nombre, String telefono, String email) {
        return jdbc.queryForObject(
                "INSERT INTO paciente (clinica_id, nombre, telefono, email) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, clinicaId, nombre, telefono, email);
    }

    public long cita(long clinicaId, long profesionalId, long servicioId, long pacienteId,
                     Instant inicio, Instant fin, int importeCentimos, String estado) {
        return jdbc.queryForObject(
                "INSERT INTO cita (clinica_id, profesional_id, servicio_id, paciente_id, inicio, fin, "
                        + "importe_centimos, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, clinicaId, profesionalId, servicioId, pacienteId,
                Timestamp.from(inicio), Timestamp.from(fin), importeCentimos, estado);
    }

    /** Cita en hora local de Madrid con la duración indicada. */
    public long cita(Escenario e, long profesionalId, long servicioId, long pacienteId,
                     LocalDate fecha, String hora, int minutos, int importe, String estado) {
        Instant inicio = instante(fecha, hora);
        return cita(e.clinica(), profesionalId, servicioId, pacienteId, inicio,
                inicio.plusSeconds(minutos * 60L), importe, estado);
    }

    public static Instant instante(LocalDate fecha, String hora) {
        return ZonedDateTime.of(fecha, LocalTime.parse(hora), MADRID).toInstant();
    }

    /** Clínica tipo Eleva con 3 profesionales, 4 servicios y 3 pacientes. */
    public Escenario escenario() {
        long clinica = clinica("eleva", "Clínica Eleva", CLAVE_ELEVA);
        long maria = profesional(clinica, "María", "fisioterapia");
        long jorge = profesional(clinica, "Jorge", "fisioterapia");
        long lucia = profesional(clinica, "Lucía", "nutricion");
        long sesionFisio = servicio(clinica, "Sesión fisioterapia", "fisioterapia", 45, 4000);
        long primeraFisio = servicio(clinica, "Primera visita fisioterapia", "fisioterapia", 60, 5000);
        long consultaNutri = servicio(clinica, "Consulta nutrición", "nutricion", 30, 3500);
        long primeraNutri = servicio(clinica, "Primera visita nutrición", "nutricion", 45, 4500);
        long ana = paciente(clinica, "Ana Pérez", "+34612345678", "ana@ejemplo.es");
        long luciaGomez = paciente(clinica, "Lucía Gómez", "+34622222222", null);
        long pedro = paciente(clinica, "Pedro Ruiz", "+34633333333", "pedro@ejemplo.es");
        return new Escenario(clinica, maria, jorge, lucia, sesionFisio, primeraFisio, consultaNutri,
                primeraNutri, ana, luciaGomez, pedro);
    }

    /** Segunda clínica para las pruebas de aislamiento (FR-003). */
    public OtraClinica otraClinica() {
        long clinica = clinica("otra", "Clínica Otra", CLAVE_OTRA);
        long profesional = profesional(clinica, "Elena", "fisioterapia");
        long servicio = servicio(clinica, "Sesión fisioterapia", "fisioterapia", 45, 4000);
        long paciente = paciente(clinica, "Lucía Martín", "+34644444444", null);
        return new OtraClinica(clinica, profesional, servicio, paciente);
    }

    /**
     * Escenario del portal (002): el de {@link #escenario()} más Teresa Méndez, Laura Romero, una
     * segunda ficha con el email de Laura («Hijo de Laura»), un paciente sin email y, en otra
     * clínica, un paciente con el email de Teresa.
     */
    public EscenarioPortal escenarioPortal() {
        Escenario e = escenario();
        long teresa = paciente(e.clinica(), "Teresa Méndez", "+34600001433", "teresa.mendez@ejemplo.es");
        long laura = paciente(e.clinica(), "Laura Romero", "+34600000471", "laura.romero@ejemplo.es");
        long hijoLaura = paciente(e.clinica(), "Hijo de Laura", "+34600000999", "Laura.Romero@ejemplo.es ");
        long sinEmail = paciente(e.clinica(), "Gonzalo Cruz", "+34600001470", null);
        OtraClinica otra = otraClinica();
        long teresaOtra = paciente(otra.clinica(), "Teresa Méndez", "+34600001433", "teresa.mendez@ejemplo.es");
        return new EscenarioPortal(e, otra, teresa, laura, hijoLaura, sinEmail, teresaOtra);
    }

    public record EscenarioPortal(Escenario e, OtraClinica otra, long teresa, long laura, long hijoLaura,
                                  long sinEmail, long teresaOtraClinica) {
    }

    public record Escenario(long clinica, long maria, long jorge, long lucia,
                            long sesionFisio, long primeraFisio, long consultaNutri, long primeraNutri,
                            long ana, long luciaGomez, long pedro) {
    }

    public record OtraClinica(long clinica, long profesional, long servicio, long paciente) {
    }
}
