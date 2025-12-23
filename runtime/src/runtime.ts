/**
 * Tachiyomi Extension Runtime
 * 
 * Provides a clean API for loading and executing Tachiyomi extensions.
 */

import type { HttpBridge, KotlinHttpResult } from './http';
import type {
  Manga,
  Chapter,
  Page,
  MangasPage,
  SourceInfo,
  FilterState,
  SettingsSchema,
} from './types';

// ============================================================================
// Extension Instance Interface
// ============================================================================

/**
 * A loaded extension instance with methods to interact with sources.
 */
export interface ExtensionInstance {
  /** Get all sources provided by this extension */
  getSources(): SourceInfo[];
  
  /** Get popular manga from a source */
  getPopularManga(sourceId: string, page: number): MangasPage;
  
  /** Get latest updates from a source */
  getLatestUpdates(sourceId: string, page: number): MangasPage;
  
  /** Search for manga */
  searchManga(sourceId: string, page: number, query: string): MangasPage;
  
  /** Get full manga details */
  getMangaDetails(sourceId: string, manga: Pick<Manga, 'url'>): Manga;
  
  /** Get chapter list for a manga */
  getChapterList(sourceId: string, manga: Pick<Manga, 'url'>): Chapter[];
  
  /** Get page list for a chapter */
  getPageList(sourceId: string, chapter: Pick<Chapter, 'url'>): Page[];
  
  /** Get available filters for a source */
  getFilterList(sourceId: string): FilterState[];
  
  /** Reset filters to default state */
  resetFilters(sourceId: string): void;
  
  /** Apply filter state (for stateful filters) */
  applyFilterState(sourceId: string, filterState: FilterState[]): void;
  
  /** Get settings schema for a source */
  getSettingsSchema(sourceId: string): SettingsSchema | null;
  
  /** Update a preference value */
  setPreference(sourceId: string, key: string, value: unknown): void;
  
  /** Get source headers (includes Referer from headersBuilder) */
  getHeaders(sourceId: string): Record<string, string>;
  
  /** 
   * Fetch image through source's client with interceptors.
   * Required for sources with image descrambling/protection.
   * Returns base64-encoded image bytes.
   */
  fetchImage(sourceId: string, pageUrl: string, imageUrl: string): string;
}

// ============================================================================
// Kotlin/JS Exports Interface
// ============================================================================

/** Internal interface matching what Kotlin/JS exports */
interface KotlinExports {
  getManifest(): string;
  getPopularManga(sourceId: string, page: number): string;
  getLatestUpdates(sourceId: string, page: number): string;
  searchManga(sourceId: string, page: number, query: string): string;
  getMangaDetails(sourceId: string, mangaUrl: string): string;
  getChapterList(sourceId: string, mangaUrl: string): string;
  getPageList(sourceId: string, chapterUrl: string): string;
  getFilterList(sourceId: string): string;
  resetFilters(sourceId: string): string;
  applyFilterState(sourceId: string, filterStateJson: string): string;
  getHeaders(sourceId: string): string;
  fetchImage(sourceId: string, pageUrl: string, imageUrl: string): string;
  getSettingsSchema?(sourceId: string): string;
  setPreference?(sourceId: string, key: string, valueJson: string): string;
}

// ============================================================================
// Result Unwrapping
// ============================================================================

interface KotlinResult<T> {
  ok: boolean;
  data?: T;
  error?: unknown;
}

function unwrapResult<T>(json: string): T {
  const result: KotlinResult<T> = JSON.parse(json);
  if (!result.ok) {
    const errMsg = typeof result.error === 'string' 
      ? result.error 
      : JSON.stringify(result.error);
    throw new Error(errMsg || 'Unknown extension error');
  }
  return result.data as T;
}

// ============================================================================
// Runtime Implementation
// ============================================================================

/**
 * Tachiyomi extension runtime.
 * 
 * Create one runtime per application, then use it to load extensions.
 */
export class TachiyomiRuntime {
  private httpBridge: HttpBridge;
  
  constructor(httpBridge: HttpBridge) {
    this.httpBridge = httpBridge;
    this.installHttpBridge();
  }
  
  /**
   * Install the HTTP bridge on globalThis for Kotlin/JS to call.
   */
  private installHttpBridge(): void {
    const bridge = this.httpBridge;
    
    (globalThis as any).tachiyomiHttpRequest = (
      url: string,
      method: string,
      headersJson: string,
      body: string | null,
      wantBytes: boolean
    ): KotlinHttpResult => {
      try {
        const headers = JSON.parse(headersJson || '{}');
        const response = bridge.request({ url, method, headers, body }, wantBytes);
        
        return {
          status: response.status,
          statusText: response.statusText,
          headersJson: JSON.stringify(response.headers),
          body: response.body,
          error: null,
        };
      } catch (e: any) {
        return {
          status: 0,
          statusText: '',
          headersJson: '{}',
          body: '',
          error: e.message || 'HTTP request failed',
        };
      }
    };
  }
  
  /**
   * Load an extension from its JavaScript code.
   * 
   * @param code - The compiled extension JavaScript
   * @returns An ExtensionInstance for interacting with the extension's sources
   */
  loadExtension(code: string): ExtensionInstance {
    // Execute the extension code
    const fn = new Function(code);
    fn();
    
    // Find the exports in globalThis
    // Extensions export to globalThis['moduleName'].tachiyomi.generated
    const g = globalThis as any;
    let exports: KotlinExports | null = null;
    
    for (const key of Object.keys(g)) {
      if (g[key]?.tachiyomi?.generated) {
        exports = g[key].tachiyomi.generated;
        break;
      }
    }
    
    if (!exports || typeof exports.getManifest !== 'function') {
      throw new Error('Invalid extension: could not find tachiyomi.generated exports');
    }
    
    return new ExtensionInstanceImpl(exports);
  }
}

/**
 * Internal implementation of ExtensionInstance
 */
class ExtensionInstanceImpl implements ExtensionInstance {
  private exports: KotlinExports;
  private sourcesCache: SourceInfo[] | null = null;
  
  constructor(exports: KotlinExports) {
    this.exports = exports;
  }
  
  getSources(): SourceInfo[] {
    if (this.sourcesCache) return this.sourcesCache;
    const json = this.exports.getManifest();
    this.sourcesCache = unwrapResult<SourceInfo[]>(json);
    return this.sourcesCache;
  }
  
  getPopularManga(sourceId: string, page: number): MangasPage {
    const json = this.exports.getPopularManga(sourceId, page);
    return unwrapResult<MangasPage>(json);
  }
  
  getLatestUpdates(sourceId: string, page: number): MangasPage {
    const json = this.exports.getLatestUpdates(sourceId, page);
    return unwrapResult<MangasPage>(json);
  }
  
  searchManga(sourceId: string, page: number, query: string): MangasPage {
    const json = this.exports.searchManga(sourceId, page, query);
    return unwrapResult<MangasPage>(json);
  }
  
  getMangaDetails(sourceId: string, manga: Pick<Manga, 'url'>): Manga {
    const json = this.exports.getMangaDetails(sourceId, manga.url);
    return unwrapResult<Manga>(json);
  }
  
  getChapterList(sourceId: string, manga: Pick<Manga, 'url'>): Chapter[] {
    const json = this.exports.getChapterList(sourceId, manga.url);
    return unwrapResult<Chapter[]>(json);
  }
  
  getPageList(sourceId: string, chapter: Pick<Chapter, 'url'>): Page[] {
    const json = this.exports.getPageList(sourceId, chapter.url);
    return unwrapResult<Page[]>(json);
  }
  
  getFilterList(sourceId: string): FilterState[] {
    const json = this.exports.getFilterList(sourceId);
    return unwrapResult<FilterState[]>(json);
  }
  
  resetFilters(sourceId: string): void {
    const json = this.exports.resetFilters(sourceId);
    unwrapResult<{ ok: boolean }>(json);
  }
  
  applyFilterState(sourceId: string, filterState: FilterState[]): void {
    const json = this.exports.applyFilterState(sourceId, JSON.stringify(filterState));
    unwrapResult<{ ok: boolean }>(json);
  }
  
  getSettingsSchema(sourceId: string): SettingsSchema | null {
    if (!this.exports.getSettingsSchema) return null;
    try {
      const json = this.exports.getSettingsSchema(sourceId);
      return unwrapResult<SettingsSchema>(json);
    } catch {
      return null;
    }
  }
  
  setPreference(sourceId: string, key: string, value: unknown): void {
    if (!this.exports.setPreference) return;
    const json = this.exports.setPreference(sourceId, key, JSON.stringify(value));
    unwrapResult<{ ok: boolean }>(json);
  }
  
  getHeaders(sourceId: string): Record<string, string> {
    const json = this.exports.getHeaders(sourceId);
    return unwrapResult<Record<string, string>>(json);
  }
  
  fetchImage(sourceId: string, pageUrl: string, imageUrl: string): string {
    const json = this.exports.fetchImage(sourceId, pageUrl, imageUrl);
    return unwrapResult<string>(json);
  }
}

/**
 * Create a Tachiyomi runtime with the given HTTP bridge.
 * 
 * @example
 * ```typescript
 * import { createRuntime } from '@tachiyomi-js/runtime';
 * 
 * const runtime = createRuntime({
 *   request(req, wantBytes) {
 *     // Your HTTP implementation here
 *     const xhr = new XMLHttpRequest();
 *     xhr.open(req.method, req.url, false);
 *     // ...
 *     return { status: 200, body: '...', headers: {}, statusText: 'OK' };
 *   }
 * });
 * 
 * const ext = runtime.loadExtension(extensionCode);
 * const sources = ext.getSources();
 * ```
 */
export function createRuntime(httpBridge: HttpBridge): TachiyomiRuntime {
  return new TachiyomiRuntime(httpBridge);
}

