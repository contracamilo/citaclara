package es.citaclara.semilla;

import es.citaclara.acceso.ServicioAcceso;
import es.citaclara.cita.Cita;
import es.citaclara.cita.EstadoCita;
import es.citaclara.cita.RepositorioCita;
import es.citaclara.clinica.Clinica;
import es.citaclara.clinica.HorarioAtencion;
import es.citaclara.clinica.Profesional;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.clinica.RepositorioHorario;
import es.citaclara.clinica.RepositorioProfesional;
import es.citaclara.clinica.RepositorioServicio;
import es.citaclara.clinica.Servicio;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.paciente.Paciente;
import es.citaclara.paciente.RepositorioPaciente;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Carga idempotente de Clínica Eleva (FR-029–FR-032): borra y recrea la clínica en una transacción
 * con el día de carga como referencia. Las citas se guardan por el mismo repositorio que las altas,
 * así que la restricción cita_sin_solape también las protege (Principio III). Es el único camino
 * exento de RN2 (FR-032).
 */
@Service
public class CargadorSemilla {

    private final RepositorioClinica clinicas;
    private final RepositorioHorario horarios;
    private final RepositorioProfesional profesionales;
    private final RepositorioServicio servicios;
    private final RepositorioPaciente pacientes;
    private final RepositorioCita citas;
    private final EntityManager em;
    private final Clock reloj;

    public CargadorSemilla(RepositorioClinica clinicas, RepositorioHorario horarios, RepositorioProfesional profesionales,
                           RepositorioServicio servicios, RepositorioPaciente pacientes, RepositorioCita citas,
                           EntityManager em, Clock reloj) {
        this.clinicas = clinicas;
        this.horarios = horarios;
        this.profesionales = profesionales;
        this.servicios = servicios;
        this.pacientes = pacientes;
        this.citas = citas;
        this.em = em;
        this.reloj = reloj;
    }

    public record Resumen(LocalDate diaDeCarga, int historia, int futuro, Map<EstadoCita, Integer> porEstado) {
    }

    @Transactional
    public Resumen cargar() {
        Instant ahora = reloj.instant();
        LocalDate diaDeCarga = ZonaClinica.fechaLocal(ahora);

        clinicas.findByIdentificador(CatalogoDemostracion.IDENTIFICADOR).ifPresent(existente -> {
            clinicas.delete(existente);
            clinicas.flush(); // ON DELETE CASCADE elimina todo lo que cuelga de ella
        });
        em.clear();

        Clinica clinica = clinicas.save(new Clinica(CatalogoDemostracion.IDENTIFICADOR, CatalogoDemostracion.NOMBRE,
                ServicioAcceso.hash(CatalogoDemostracion.CLAVE)));
        for (int dia = 1; dia <= 5; dia++) {
            horarios.save(new HorarioAtencion(clinica.getId(), dia, CatalogoDemostracion.APERTURA, CatalogoDemostracion.CIERRE));
        }
        List<Profesional> listaProfesionales = new ArrayList<>();
        CatalogoDemostracion.PROFESIONALES.forEach(p ->
                listaProfesionales.add(profesionales.save(new Profesional(clinica.getId(), p.nombre(), p.especialidad()))));
        List<Servicio> listaServicios = new ArrayList<>();
        CatalogoDemostracion.SERVICIOS.forEach(s -> listaServicios.add(servicios.save(
                new Servicio(clinica.getId(), s.nombre(), s.especialidad(), s.minutos(), s.precioCentimos()))));
        List<Paciente> listaPacientes = new ArrayList<>();
        CatalogoDemostracion.PACIENTES.forEach(p ->
                listaPacientes.add(pacientes.save(new Paciente(clinica.getId(), p.nombre(), p.telefono(), p.email()))));

        int historia = 0;
        int futuro = 0;
        Map<EstadoCita, Integer> porEstado = new EnumMap<>(EstadoCita.class);
        List<Cita> nuevas = new ArrayList<>();
        for (CitaSemilla c : GeneradorSemilla.generar()) {
            Servicio servicio = listaServicios.get(c.servicio());
            Instant inicio = ZonaClinica.aInstante(DiasLaborables.fecha(diaDeCarga, c.posicion()), c.inicio());
            nuevas.add(new Cita(clinica.getId(), listaProfesionales.get(c.profesional()), servicio,
                    listaPacientes.get(c.paciente()), inicio, inicio.plusSeconds(servicio.getDuracionMinutos() * 60L),
                    servicio.getPrecioCentimos(), c.estado(), ahora));
            porEstado.merge(c.estado(), 1, Integer::sum);
            if (c.posicion() < 0) {
                historia++;
            } else {
                futuro++;
            }
        }
        citas.saveAllAndFlush(nuevas);
        return new Resumen(diaDeCarga, historia, futuro, porEstado);
    }
}
