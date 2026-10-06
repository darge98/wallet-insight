package it.walletinsight.platform.web;

import it.walletinsight.shared.page.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Forma JSON di una pagina, identica a `Page<T>` di `shared/page.ts`.
 *
 * Esiste come DTO separato dal record di dominio per la stessa ragione degli
 * altri: il contratto HTTP non deve cambiare quando cambia il dominio.
 */
public record PageResponse<T>(List<T> items, long total, int index, int size, int pageCount) {

    public static <T, R> PageResponse<R> from(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.total(),
                page.index(),
                page.size(),
                page.pageCount());
    }
}
