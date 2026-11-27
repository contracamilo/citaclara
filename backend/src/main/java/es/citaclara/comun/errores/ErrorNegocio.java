package es.citaclara.comun.errores;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error con significado para la clínica. Se traduce a Problem Details con {@code codigo} (para el
 * programa, nunca se muestra) y {@code mensaje} (español de España, listo para mostrarse; FR-027).
 */
public class ErrorNegocio extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    public ErrorNegocio(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public ErrorNegocio con(String propiedad, Object valor) {
        extras.put(propiedad, valor);
        return this;
    }

    public HttpStatus estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, Object> extras() {
        return extras;
    }

    public static ErrorNegocio noEncontrado() {
        return new ErrorNegocio(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", Mensajes.NO_ENCONTRADO);
    }
}
