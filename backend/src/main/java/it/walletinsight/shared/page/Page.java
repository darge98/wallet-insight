package it.walletinsight.shared.page;

import java.util.List;

/**
 * Pagina di risultati.
 *
 * La forma è quella che il frontend si aspetta (`shared/page.ts`):
 * `{items, total, index, size, pageCount}`. Non usare mai
 * `org.springframework.data.domain.Page`, che serializza `content` e
 * `totalElements` e romperebbe il contratto.
 */
public record Page<T>(List<T> items, long total, int index, int size, int pageCount) {

    public Page {
        items = List.copyOf(items);
    }

    public static <T> Page<T> of(List<T> items, long total, PageRequest request) {
        int pageCount = (int) Math.ceil((double) total / request.size());
        return new Page<>(items, total, request.index(), request.size(), pageCount);
    }

    public static <T> Page<T> empty() {
        return new Page<>(List.of(), 0L, 0, PageRequest.DEFAULT_SIZE, 0);
    }
}
