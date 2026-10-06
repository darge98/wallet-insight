package it.walletinsight.shared.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void calcolaIlNumeroDiPagine() {
        Page<Integer> pagina = Page.of(List.of(3, 4, 5), 7L, PageRequest.of(1, 3));

        assertThat(pagina.items()).containsExactly(3, 4, 5);
        assertThat(pagina.pageCount()).isEqualTo(3);
        assertThat(pagina.total()).isEqualTo(7L);
        assertThat(pagina.index()).isEqualTo(1);
    }

    @Test
    void unaCollezioneVuotaHaZeroPagine() {
        Page<Integer> pagina = Page.of(List.of(), 0L, PageRequest.of(0, 10));

        assertThat(pagina.items()).isEmpty();
        assertThat(pagina.pageCount()).isZero();
        assertThat(pagina.total()).isZero();
    }

    @Test
    void laPaginaVuotaUsaLaDimensionePredefinita() {
        assertThat(Page.empty().size()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(Page.empty().total()).isZero();
    }

    @Test
    void calcolaLoScostamentoPerLaQuery() {
        assertThat(PageRequest.of(0, 25).offset()).isZero();
        assertThat(PageRequest.of(3, 25).offset()).isEqualTo(75L);
    }

    @Test
    void riportaIParametriFuoriRangeDentroILimiti() {
        assertThat(PageRequest.of(-5, 25).index()).isZero();
        assertThat(PageRequest.of(0, 0).size()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(PageRequest.of(0, 5_000).size()).isEqualTo(PageRequest.MAX_SIZE);
    }
}
