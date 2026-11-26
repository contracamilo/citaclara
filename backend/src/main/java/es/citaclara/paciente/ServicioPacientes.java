package es.citaclara.paciente;

import es.citaclara.comun.Telefono;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Búsqueda (FR-018) y alta de fichas de paciente (FR-019, FR-020). */
@Service
public class ServicioPacientes {

    private final RepositorioPaciente pacientes;

    public ServicioPacientes(RepositorioPaciente pacientes) {
        this.pacientes = pacientes;
    }

    @Transactional(readOnly = true)
    public List<PacienteRespuesta> buscar(Long clinicaId, String q) {
        String texto = q == null ? "" : q.strip();
        if (texto.length() < 2) {
            throw new ErrorNegocio(HttpStatus.BAD_REQUEST, "DATOS_NO_VALIDOS",
                    "Escribe al menos 2 letras o cifras para buscar.");
        }
        String cifras = Telefono.soloCifras(texto);
        return pacientes.buscar(clinicaId, texto, cifras.length() >= 2 ? cifras : "").stream()
                .map(PacienteRespuesta::desde).toList();
    }

    /** Crea la ficha dentro de la transacción del llamador; teléfono repetido → PACIENTE_DUPLICADO. */
    public Paciente crear(Long clinicaId, NuevoPaciente nuevo) {
        String telefono = Telefono.normalizar(nuevo.telefono())
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.BAD_REQUEST, "DATOS_NO_VALIDOS", Mensajes.DATOS_NO_VALIDOS));
        pacientes.findByClinicaIdAndTelefono(clinicaId, telefono).ifPresent(existente -> {
            throw duplicado(existente);
        });
        return pacientes.saveAndFlush(new Paciente(clinicaId, nuevo.nombre(), telefono, nuevo.email()));
    }

    /** Relee la ficha que ya tiene ese teléfono (tras una carrera con la restricción de unicidad). */
    @Transactional(readOnly = true)
    public ErrorNegocio duplicadoDe(Long clinicaId, String telefonoSinNormalizar) {
        return Telefono.normalizar(telefonoSinNormalizar)
                .flatMap(t -> pacientes.findByClinicaIdAndTelefono(clinicaId, t))
                .map(ServicioPacientes::duplicado)
                .orElseGet(() -> new ErrorNegocio(HttpStatus.CONFLICT, "PACIENTE_DUPLICADO",
                        "Ya hay un paciente con ese teléfono."));
    }

    static ErrorNegocio duplicado(Paciente existente) {
        return new ErrorNegocio(HttpStatus.CONFLICT, "PACIENTE_DUPLICADO",
                "Ya hay un paciente con ese teléfono: " + existente.getNombre() + ". ¿Quieres usar su ficha?")
                .con("pacienteExistente", PacienteRespuesta.desde(existente));
    }
}
