package es.citaclara.comun.errores;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Todos los errores del API salen como Problem Details (RFC 9457) con {@code codigo} y
 * {@code mensaje} en español de España, sin trazas ni identificadores internos (FR-027).
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<ProblemDetail> negocio(ErrorNegocio error) {
        ProblemDetail problema = problema(error.estado(), error.codigo(), error.getMessage());
        error.extras().forEach(problema::setProperty);
        return ResponseEntity.status(error.estado()).body(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> datosNoValidos(MethodArgumentNotValidException error) {
        List<Map<String, String>> errores = new ArrayList<>();
        for (FieldError campo : error.getBindingResult().getFieldErrors()) {
            errores.add(Map.of("campo", campo.getField(), "mensaje", textoOGenerico(campo.getDefaultMessage())));
        }
        error.getBindingResult().getGlobalErrors().forEach(global ->
                errores.add(Map.of("campo", "", "mensaje", textoOGenerico(global.getDefaultMessage()))));
        return datosNoValidos(errores);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> peticionMalFormada(Exception error) {
        return datosNoValidos(List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> tipoNoAdmitido() {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_ADMITIDO", Mensajes.TIPO_NO_ADMITIDO);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> noEncontrado() {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", Mensajes.NO_ENCONTRADO);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNoAdmitido() {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_ADMITIDO", Mensajes.TIPO_NO_ADMITIDO);
    }

    /**
     * Red de seguridad para cualquier camino de escritura: si la restricción de no solape salta
     * fuera del alta (que la traduce con más contexto), se explica igual (research D2).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integridad(DataIntegrityViolationException error) {
        if (Restricciones.esSolape(error)) {
            return respuesta(HttpStatus.CONFLICT, "HUECO_RECIEN_OCUPADO", Restricciones.MENSAJE_HUECO_RECIEN_OCUPADO);
        }
        LOG.error("Violación de integridad no prevista: {}", resumenSinDatos(error));
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", Mensajes.ERROR_INTERNO);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception error) {
        LOG.error("Error no controlado: {}", resumenSinDatos(error));
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", Mensajes.ERROR_INTERNO);
    }

    /**
     * Resumen de una excepción apto para el registro: clases de la cadena de causas, {@code SQLState}
     * y el primer método propio donde ocurrió. Nunca el mensaje, que puede llevar datos personales
     * (por ejemplo, el valor de una restricción de unicidad violada). Spec 005, H-03.
     */
    static String resumenSinDatos(Throwable error) {
        StringBuilder sb = new StringBuilder();
        String sqlState = null;
        Throwable actual = error;
        for (int i = 0; actual != null && i < 8; i++, actual = actual.getCause()) {
            if (i > 0) {
                sb.append(" <- ");
            }
            sb.append(actual.getClass().getName());
            if (sqlState == null && actual instanceof java.sql.SQLException sql) {
                sqlState = sql.getSQLState();
            }
        }
        if (sqlState != null) {
            sb.append(" sqlState=").append(sqlState);
        }
        for (StackTraceElement marco : error.getStackTrace()) {
            if (marco.getClassName().startsWith("es.citaclara")) {
                sb.append(" en ").append(marco.getClassName()).append('.').append(marco.getMethodName())
                        .append(':').append(marco.getLineNumber());
                break;
            }
        }
        return sb.toString();
    }

    private static ResponseEntity<ProblemDetail> datosNoValidos(List<Map<String, String>> errores) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "DATOS_NO_VALIDOS", Mensajes.DATOS_NO_VALIDOS);
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(problema(estado, codigo, mensaje));
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String mensaje) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setTitle(mensaje);
        problema.setProperty("codigo", codigo);
        problema.setProperty("mensaje", mensaje);
        return problema;
    }

    private static String textoOGenerico(String texto) {
        return texto == null || texto.isBlank() ? Mensajes.DATOS_NO_VALIDOS : texto;
    }
}
