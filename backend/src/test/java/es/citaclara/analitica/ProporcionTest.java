package es.citaclara.analitica;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProporcionTest {

    @Test
    @DisplayName("[004][FR-010] el porcentaje se redondea half-up una sola vez, con enteros")
    void redondeoHalfUp() {
        assertThat(Proporcion.de(30, 321).decimas()).isEqualTo(93);   // 9,3458 %
        assertThat(Proporcion.de(37, 320).decimas()).isEqualTo(116);  // 11,5625 %
        assertThat(Proporcion.de(1, 39).decimas()).isEqualTo(26);     // 2,564 %
        assertThat(Proporcion.de(5, 41).decimas()).isEqualTo(122);    // 12,195 %
        assertThat(Proporcion.de(4, 55).decimas()).isEqualTo(73);     // 7,27 %
        assertThat(Proporcion.de(1, 16).decimas()).isEqualTo(63);     // 6,25 % → empate hacia arriba
        assertThat(Proporcion.de(1, 8).decimas()).isEqualTo(125);     // 12,5 % exacto
        assertThat(Proporcion.de(0, 10).decimas()).isZero();
    }

    @Test
    @DisplayName("[004][FR-011] un total 0 da décimas nulas (Sin datos), nunca 0 %")
    void sinBase() {
        Proporcion sinBase = Proporcion.de(0, 0);
        assertThat(sinBase.decimas()).isNull();
        assertThat(sinBase.total()).isZero();
    }

    @Test
    @DisplayName("[004][FR-010] parte = total da 1000 décimas y se conservan parte y total exactos")
    void todo() {
        Proporcion todo = Proporcion.de(7, 7);
        assertThat(todo.decimas()).isEqualTo(1000);
        assertThat(todo.parte()).isEqualTo(7);
        assertThat(todo.total()).isEqualTo(7);
        assertThatThrownBy(() -> Proporcion.de(-1, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
