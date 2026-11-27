package es.citaclara.recordatorio;

import es.citaclara.cita.Cita;
import es.citaclara.cita.RepositorioCita;
import es.citaclara.clinica.Clinica;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.correo.EnviadorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proceso diario de recordatorios (FR-001–FR-008). Ventana [ahora + 24 h, siguiente ejecución + 24 h),
 * donde la siguiente ejecución es la misma hora local del día siguiente: las ventanas de ejecuciones
 * consecutivas encajan sin huecos ni solapes aunque el día tenga 23 o 25 horas (research D1, D4).
 * Cada cita se procesa en su propia transacción (research D3).
 */
@Service
public class ServicioRecordatorios {

    static final Duration ANTELACION = Duration.ofHours(24);

    private static final Logger REGISTRO = LoggerFactory.getLogger(ServicioRecordatorios.class);

    private final RepositorioCita citas;
    private final RepositorioClinica clinicas;
    private final RepositorioRecordatorio recordatorios;
    private final ConstructorCorreoRecordatorio constructor;
    private final EnviadorCorreo enviador;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioRecordatorios(RepositorioCita citas, RepositorioClinica clinicas,
                                 RepositorioRecordatorio recordatorios, ConstructorCorreoRecordatorio constructor,
                                 EnviadorCorreo enviador, Clock reloj, PlatformTransactionManager transacciones) {
        this.citas = citas;
        this.clinicas = clinicas;
        this.recordatorios = recordatorios;
        this.constructor = constructor;
        this.enviador = enviador;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Genera los recordatorios que faltan y devuelve cuántos ha generado en esta ejecución. */
    public int generar() {
        Instant ahora = reloj.instant();
        Instant siguienteEjecucion = ahora.atZone(ZonaClinica.ZONA).plusDays(1).toInstant();
        List<Cita> pendientes = citas.paraRecordatorio(ahora.plus(ANTELACION), siguienteEjecucion.plus(ANTELACION));
        Map<Long, Clinica> clinicasLeidas = new HashMap<>();
        int generados = 0;
        for (Cita cita : pendientes) {
            try {
                Clinica clinica = clinicasLeidas.computeIfAbsent(cita.getClinicaId(),
                        id -> clinicas.findById(id).orElseThrow());
                if (Boolean.TRUE.equals(transaccion.execute(estado -> generarUno(cita, clinica, ahora)))) {
                    generados++;
                }
            } catch (RuntimeException fallo) {
                // FR-007: un fallo no detiene a las demás ni deja la cita registrada como enviada.
                // Sin el mensaje de la excepción: podría incluir la dirección del paciente.
                REGISTRO.warn("No se ha generado el recordatorio de la cita {} ({})", cita.getId(),
                        fallo.getClass().getSimpleName());
            }
        }
        REGISTRO.info("Recordatorios generados: {} de {} citas en la ventana", generados, pendientes.size());
        return generados;
    }

    private boolean generarUno(Cita cita, Clinica clinica, Instant ahora) {
        String enlace = EnlaceCancelacion.nuevo();
        int filas = recordatorios.insertarSiNoExiste(cita.getClinicaId(), cita.getId(),
                cita.getPaciente().getEmail().trim(), EnlaceCancelacion.huella(enlace), ahora);
        if (filas == 0) {
            return false; // otra ejecución ya lo generó
        }
        enviador.enviar(constructor.construir(cita, clinica, enlace, ahora));
        return true;
    }
}
