package es.citaclara.seguridad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analizador del informe de auditoría y de los requisitos de pagos de la spec 005.
 * El formato está en {@code specs/005-security-audit/contracts/informe-auditoria.md}.
 */
final class LectorInforme {

    static final Path RAIZ = localizarRaiz();
    static final Path CARPETA = RAIZ.resolve("specs/005-security-audit");

    record Hallazgo(String id, String titulo, Map<String, String> campos) {
        String campo(String clave) {
            return campos.getOrDefault(clave, "").strip();
        }
    }

    record Veredicto(String categoria, String veredicto, String hallazgos, String evidencia) {
    }

    private final String texto;

    LectorInforme(Path fichero) {
        try {
            this.texto = Files.readString(fichero);
        } catch (IOException e) {
            throw new IllegalStateException("No se puede leer " + fichero, e);
        }
    }

    static LectorInforme informe() {
        return new LectorInforme(CARPETA.resolve("informe-auditoria.md"));
    }

    static LectorInforme pagos() {
        return new LectorInforme(CARPETA.resolve("requisitos-pagos.md"));
    }

    String texto() {
        return texto;
    }

    /** Valor de una línea de la cabecera de reproducibilidad: {@code - **Clave**: valor}. */
    String cabecera(String clave) {
        Matcher m = Pattern.compile("(?m)^- \\*\\*" + Pattern.quote(clave) + "\\*\\*:\\s*(.+)$").matcher(texto);
        return m.find() ? m.group(1).strip() : "";
    }

    /** Filas de la tabla de veredictos (las que empiezan por una categoría A01..A10). */
    List<Veredicto> veredictos() {
        List<Veredicto> filas = new ArrayList<>();
        for (String linea : texto.split("\n")) {
            if (linea.matches("^\\|\\s*A(0[1-9]|10)\\b.*")) {
                String[] c = linea.split("\\|", -1);
                if (c.length >= 5) {
                    filas.add(new Veredicto(c[1].strip(), c[2].strip(), c[3].strip(), c[4].strip()));
                }
            }
        }
        return filas;
    }

    /** Fichas {@code ### H-nn · título} con su lista de claves en negrita. */
    List<Hallazgo> hallazgos() {
        List<Hallazgo> lista = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^### (H-\\d{2,3}) · (.+)$").matcher(texto);
        List<int[]> posiciones = new ArrayList<>();
        List<String[]> cabeceras = new ArrayList<>();
        while (m.find()) {
            posiciones.add(new int[]{m.start(), m.end()});
            cabeceras.add(new String[]{m.group(1), m.group(2).strip()});
        }
        for (int i = 0; i < posiciones.size(); i++) {
            int fin = i + 1 < posiciones.size() ? posiciones.get(i + 1)[0] : texto.length();
            String cuerpo = texto.substring(posiciones.get(i)[1], fin);
            int otraSeccion = cuerpo.indexOf("\n## ");
            if (otraSeccion >= 0) {
                cuerpo = cuerpo.substring(0, otraSeccion);
            }
            Map<String, String> campos = new LinkedHashMap<>();
            Matcher c = Pattern.compile("(?m)^- \\*\\*([^*]+)\\*\\*:\\s*(.*)$").matcher(cuerpo);
            while (c.find()) {
                campos.put(c.group(1).strip(), c.group(2).strip());
            }
            lista.add(new Hallazgo(cabeceras.get(i)[0], cabeceras.get(i)[1], campos));
        }
        return lista;
    }

    boolean tieneSeccion(String titulo) {
        return Pattern.compile("(?m)^## " + Pattern.quote(titulo) + "\\s*$").matcher(texto).find();
    }

    /** Contenido de una sección {@code ## título} hasta la siguiente de nivel 2 (o el final). */
    String seccion(String titulo) {
        Matcher m = Pattern.compile("(?m)^## " + Pattern.quote(titulo) + "\\s*$").matcher(texto);
        if (!m.find()) {
            return "";
        }
        int inicio = m.end();
        Matcher siguiente = Pattern.compile("(?m)^## ").matcher(texto);
        int fin = texto.length();
        if (siguiente.find(inicio)) {
            fin = siguiente.start();
        }
        return texto.substring(inicio, fin);
    }

    private static Path localizarRaiz() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("specs/MAPA.md"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("No se encuentra la raíz del repositorio (specs/MAPA.md)");
        }
        return p;
    }
}
