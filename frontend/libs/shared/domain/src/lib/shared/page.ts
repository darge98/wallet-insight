export interface PageRequest {
  /** Indice di pagina a base 0. */
  readonly index: number;
  readonly size: number;
}

export interface Page<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly index: number;
  readonly size: number;
  readonly pageCount: number;
}

export const DEFAULT_PAGE_SIZE = 25;

export function emptyPage<T>(size: number = DEFAULT_PAGE_SIZE): Page<T> {
  return { items: [], total: 0, index: 0, size, pageCount: 0 };
}

export function buildPage<T>(all: readonly T[], request: PageRequest): Page<T> {
  const pageCount = Math.max(1, Math.ceil(all.length / request.size));
  const index = Math.min(Math.max(request.index, 0), pageCount - 1);
  const start = index * request.size;

  return {
    items: all.slice(start, start + request.size),
    total: all.length,
    index,
    size: request.size,
    pageCount,
  };
}
