package es.citaclara.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Modo simulado (FR-020): cada correo se guarda como fichero {@code .eml} en el directorio de salida
 * y nunca se envía a una dirección real. Escritura a un temporal y movimiento atómico: jamás queda
 * un {@code .eml} a medias.
 */
public class EnviadorCorreoFichero implements EnviadorCorreo {

    private final Path directorio;
    private final String remitente;

    public EnviadorCorreoFichero(Path directorio, String remitente) {
        this.directorio = directorio;
        this.remitente = remitente;
    }

    @Override
    public void enviar(CorreoSaliente correo) {
        MimeMessage mensaje = MensajeMime.construir(remitente, correo);
        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            temporal = Files.createTempFile(directorio, ".correo-", ".tmp");
            try (OutputStream salida = Files.newOutputStream(temporal)) {
                mensaje.writeTo(salida);
            }
            legiblePorTodos(temporal);
            Files.move(temporal, directorio.resolve(correo.nombreFichero()),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | MessagingException e) {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                    // el fallo relevante es el original
                }
            }
            throw new CorreoNoEnviadoException("No se ha podido guardar el correo simulado", e);
        }
    }

    /**
     * {@code createTempFile} crea el fichero con permisos 0600; en Linux el .eml pertenece al usuario
     * del contenedor y quien lo lee desde fuera (la batería e2e en CI) no podría abrirlo.
     */
    private static void legiblePorTodos(Path fichero) throws IOException {
        try {
            Files.setPosixFilePermissions(fichero, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException noPosix) {
            // sistema de ficheros sin permisos POSIX: ya es legible
        }
    }
}
