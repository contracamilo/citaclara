package es.citaclara.recordatorio;

import es.citaclara.correo.EnviadorCorreo;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-022: nada de SMS, WhatsApp, plantillas, plazos configurables ni estadísticas «por si acaso». */
class AlcanceV1Test extends PruebaIntegracionBase {

    @Autowired
    ApplicationContext contexto;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    @Test
    @DisplayName("[003][FR-022] las únicas rutas nuevas son GET y POST /api/cancelacion/{enlace}, sin rutas de administración de recordatorios")
    void soloLasRutasDeCancelacion() {
        List<String> nuevas = rutas.getHandlerMethods().keySet().stream()
                .filter(info -> patrones(info).stream().anyMatch(p -> esDeCancelacion(p) || p.contains("recordatorio")))
                .flatMap(info -> info.getMethodsCondition().getMethods().stream()
                        .map(RequestMethod::name).map(metodo -> metodo + " " + patrones(info).iterator().next()))
                .sorted().toList();

        assertThat(nuevas).containsExactly("GET /api/cancelacion/{enlace}", "POST /api/cancelacion/{enlace}");
    }

    @Test
    @DisplayName("[003][FR-022] solo existe un envío de correo (por correo electrónico): no hay canales SMS ni WhatsApp")
    void soloCorreo() {
        assertThat(contexto.getBeansOfType(EnviadorCorreo.class)).hasSize(1);
        assertThat(Arrays.asList(contexto.getBeanDefinitionNames()))
                .noneMatch(nombre -> nombre.toLowerCase().contains("sms") || nombre.toLowerCase().contains("whatsapp"));
    }

    /** Segmento exacto: `/api/cancelaciones-portal` es de la 002, no de la 003. */
    private static boolean esDeCancelacion(String patron) {
        return patron.equals("/api/cancelacion") || patron.startsWith("/api/cancelacion/");
    }

    private static java.util.Set<String> patrones(RequestMappingInfo info) {
        return info.getPathPatternsCondition() == null ? java.util.Set.of()
                : info.getPathPatternsCondition().getPatternValues();
    }
}
