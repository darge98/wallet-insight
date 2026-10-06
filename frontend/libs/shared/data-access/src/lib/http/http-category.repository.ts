import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  Category,
  CategoryChanges,
  CategoryId,
  CategoryRejectedError,
  CategoryRepository,
  SourceCategory,
  SourceCategoryId,
} from '@wallet/shared-domain';

import {
  CategoryResponse,
  SourceCategoryResponse,
  toCategory,
  toSourceCategory,
} from './movement-contract';
import { API_BASE_URL } from './api-base-url';
import { currentUserId } from './current-user';
import { requestJson } from './json-request';

/** Le categorie di Wallet Insights e gli agganci con le sorgenti, lette e scritte sul backend. */
@Injectable()
export class HttpCategoryRepository implements CategoryRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findAll(signal?: AbortSignal): Promise<readonly Category[]> {
    const categories = await requestJson<readonly CategoryResponse[]>(
      this.http,
      'GET',
      await this.url(signal),
      { signal },
    );
    return categories.map(toCategory);
  }

  async create(name: string, parentId: CategoryId | null, signal?: AbortSignal): Promise<Category> {
    const url = await this.url(signal);
    return toCategory(
      await rejectingRules(
        requestJson<CategoryResponse>(this.http, 'POST', url, {
          body: { name, parentId: parentId ?? undefined },
          signal,
        }),
      ),
    );
  }

  async update(id: CategoryId, changes: CategoryChanges, signal?: AbortSignal): Promise<Category> {
    const url = `${await this.url(signal)}/${id}`;
    return toCategory(
      await rejectingRules(
        requestJson<CategoryResponse>(this.http, 'PATCH', url, { body: changes, signal }),
      ),
    );
  }

  async remove(id: CategoryId, into: CategoryId | null, signal?: AbortSignal): Promise<void> {
    const url = `${await this.url(signal)}/${id}${into ? `?into=${into}` : ''}`;
    // Un 204 non ha corpo: `requestJson` lo considererebbe un contratto rotto.
    await rejectingRules(
      new Promise<void>((resolve, reject) => {
        const subscription = this.http.delete(url, { observe: 'response' }).subscribe({
          next: () => resolve(),
          error: reject,
        });
        signal?.addEventListener(
          'abort',
          () => {
            subscription.unsubscribe();
            reject(signal.reason);
          },
          { once: true },
        );
      }),
    );
  }

  async findSources(signal?: AbortSignal): Promise<readonly SourceCategory[]> {
    const sources = await requestJson<readonly SourceCategoryResponse[]>(
      this.http,
      'GET',
      `${await this.url(signal)}/sources`,
      { signal },
    );
    return sources.map(toSourceCategory);
  }

  async relink(
    id: SourceCategoryId,
    categoryId: CategoryId,
    signal?: AbortSignal,
  ): Promise<SourceCategory> {
    const url = `${await this.url(signal)}/sources/${id}`;
    return toSourceCategory(
      await rejectingRules(
        requestJson<SourceCategoryResponse>(this.http, 'PUT', url, {
          body: { categoryId },
          signal,
        }),
      ),
    );
  }

  private async url(signal?: AbortSignal): Promise<string> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    return `${this.baseUrl}/users/${userId}/categories`;
  }
}

/** 400 e 409 portano nel `detail` un motivo scritto per l'utente. */
async function rejectingRules<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (cause) {
    if (cause instanceof HttpErrorResponse && (cause.status === 409 || cause.status === 400)) {
      const detail = (cause.error as { detail?: unknown } | null)?.detail;
      throw new CategoryRejectedError(
        typeof detail === 'string' ? detail : 'Questa modifica non è ammessa.',
      );
    }
    throw cause;
  }
}
