/**
 * Web Worker for running Tachiyomi Kotlin/JS extension sources
 * Sync XHR works in workers, enabling blocking HTTP calls from Kotlin/JS
 */
import * as Comlink from "comlink";
import { createRuntime, type ExtensionInstance } from "../runtime";
import { createSyncXhrBridge } from "../http/sync-xhr";
import type {
  MangasPage,
  Manga,
  Chapter,
  Page,
  FilterState,
  SourceInfo,
  ExtensionManifest,
} from "../types";

// ============ Preferences Storage ============
// SharedPreferences implementation for Kotlin/JS

const prefsStorage = new Map<string, Map<string, unknown>>();
let pendingPrefChanges: Array<{ name: string; key: string; value: unknown }> = [];

class SharedPreferencesImpl {
  private name: string;
  private data: Map<string, unknown>;

  constructor(name: string) {
    this.name = name;
    if (!prefsStorage.has(name)) {
      prefsStorage.set(name, new Map());
    }
    this.data = prefsStorage.get(name)!;
  }

  getString(key: string, defValue: string | null): string | null {
    const val = this.data.get(key);
    return typeof val === "string" ? val : defValue;
  }

  getBoolean(key: string, defValue: boolean): boolean {
    const val = this.data.get(key);
    return typeof val === "boolean" ? val : defValue;
  }

  getInt(key: string, defValue: number): number {
    const val = this.data.get(key);
    return typeof val === "number" ? Math.floor(val) : defValue;
  }

  getLong(key: string, defValue: number): number {
    return this.getInt(key, defValue);
  }

  getFloat(key: string, defValue: number): number {
    const val = this.data.get(key);
    return typeof val === "number" ? val : defValue;
  }

  getStringSet(key: string, defValue: string[] | null): string[] | null {
    const val = this.data.get(key);
    return Array.isArray(val) ? val : defValue;
  }

  getAll(): Record<string, unknown> {
    const result: Record<string, unknown> = {};
    for (const [k, v] of this.data.entries()) {
      result[k] = v;
    }
    return result;
  }

  contains(key: string): boolean {
    return this.data.has(key);
  }

  edit(): SharedPreferencesEditor {
    return new SharedPreferencesEditor(this.name, this.data);
  }
}

class SharedPreferencesEditor {
  private name: string;
  private data: Map<string, unknown>;
  private changes: Map<string, unknown | null> = new Map();

  constructor(name: string, data: Map<string, unknown>) {
    this.name = name;
    this.data = data;
  }

  putString(key: string, value: string | null): this {
    this.changes.set(key, value);
    return this;
  }

  putBoolean(key: string, value: boolean): this {
    this.changes.set(key, value);
    return this;
  }

  putInt(key: string, value: number): this {
    this.changes.set(key, Math.floor(value));
    return this;
  }

  putLong(key: string, value: number): this {
    return this.putInt(key, value);
  }

  putFloat(key: string, value: number): this {
    this.changes.set(key, value);
    return this;
  }

  putStringSet(key: string, value: string[] | null): this {
    this.changes.set(key, value);
    return this;
  }

  remove(key: string): this {
    this.changes.set(key, null);
    return this;
  }

  clear(): this {
    for (const key of this.data.keys()) {
      this.changes.set(key, null);
    }
    return this;
  }

  apply(): void {
    this.commit();
  }

  commit(): boolean {
    for (const [key, value] of this.changes) {
      if (value === null) {
        this.data.delete(key);
      } else {
        this.data.set(key, value);
      }
      pendingPrefChanges.push({ name: this.name, key, value });
    }
    this.changes.clear();
    return true;
  }
}

// Expose SharedPreferences factory to Kotlin/JS
(globalThis as Record<string, unknown>).getSharedPreferences = (name: string) => {
  return new SharedPreferencesImpl(name);
};

// ============ Worker API ============

let proxyUrlFn: ((url: string) => string) | null = null;
let runtime: ReturnType<typeof createRuntime> | null = null;
let extension: ExtensionInstance | null = null;
let manifest: ExtensionManifest | null = null;

/**
 * Initialize the runtime with proxy URL
 */
function initRuntime(proxyUrl: string | null): void {
  proxyUrlFn = proxyUrl ? (url: string) => `${proxyUrl}${encodeURIComponent(url)}` : null;
  const httpBridge = createSyncXhrBridge({ proxyUrl: proxyUrlFn ?? undefined });
  runtime = createRuntime(httpBridge);
}

/**
 * Worker API exposed via Comlink
 */
const workerApi = {
  // ============ Preferences Methods ============

  initPreferences(prefsName: string, values: Record<string, unknown>): void {
    const prefs = new Map<string, unknown>();
    for (const [key, value] of Object.entries(values)) {
      prefs.set(key, value);
    }
    prefsStorage.set(prefsName, prefs);
  },

  flushPrefChanges(): Array<{ name: string; key: string; value: unknown }> {
    const changes = pendingPrefChanges;
    pendingPrefChanges = [];
    return changes;
  },

  getSettingsSchema(sourceId: string): string | null {
    if (!extension) return null;
    try {
      const schema = extension.getSettingsSchema(sourceId);
      return schema ? JSON.stringify(schema) : null;
    } catch (e) {
      console.error("[Tachiyomi Worker] getSettingsSchema error:", e);
      return null;
    }
  },

  // ============ Load Extension ============

  async load(
    jsUrl: string,
    manifestData: ExtensionManifest,
    proxyUrl: string | null
  ): Promise<{ success: boolean; manifest?: ExtensionManifest }> {
    try {
      console.log("[Tachiyomi Worker] Loading JS from:", jsUrl);
      
      // Initialize runtime with proxy
      initRuntime(proxyUrl);
      if (!runtime) throw new Error("Failed to initialize runtime");

      manifest = manifestData;

      // Fetch the extension code
      const response = await fetch(jsUrl);
      if (!response.ok) {
        throw new Error(`Failed to fetch extension: ${response.status}`);
      }
      const code = await response.text();

      // Load extension using the runtime
      extension = runtime.loadExtension(code);

      // Get sources metadata
      const sources = extension.getSources();
      manifest.sources = sources as SourceInfo[];

      console.log("[Tachiyomi Worker] Loaded, sources:", sources.length, sources.map(s => s.name).slice(0, 5));

      return { success: sources.length > 0, manifest };
    } catch (e) {
      console.error("[Tachiyomi Worker] Failed to load:", e);
      return { success: false };
    }
  },

  isLoaded(): boolean {
    return extension !== null;
  },

  getManifest(): ExtensionManifest | null {
    return manifest;
  },

  getSources(): SourceInfo[] {
    return manifest?.sources ?? [];
  },

  // ============ Filter Methods ============

  getFilterList(sourceId: string): FilterState[] {
    if (!extension) return [];
    try {
      return extension.getFilterList(sourceId);
    } catch (e) {
      console.error("[Tachiyomi Worker] getFilterList error:", e);
      return [];
    }
  },

  resetFilters(sourceId: string): boolean {
    if (!extension) return false;
    try {
      extension.resetFilters(sourceId);
      return true;
    } catch (e) {
      console.error("[Tachiyomi Worker] resetFilters error:", e);
      return false;
    }
  },

  applyFilterState(sourceId: string, filterStateJson: string): boolean {
    if (!extension) return false;
    try {
      const filterState = JSON.parse(filterStateJson) as FilterState[];
      extension.applyFilterState(sourceId, filterState);
      return true;
    } catch (e) {
      console.error("[Tachiyomi Worker] applyFilterState error:", e);
      return false;
    }
  },

  // ============ Data Methods ============

  getPopularManga(sourceId: string, page: number): MangasPage {
    if (!extension) return { mangas: [], hasNextPage: false };
    try {
      return extension.getPopularManga(sourceId, page);
    } catch (e) {
      console.error("[Tachiyomi Worker] getPopularManga error:", e);
      return { mangas: [], hasNextPage: false };
    }
  },

  getLatestUpdates(sourceId: string, page: number): MangasPage {
    if (!extension) return { mangas: [], hasNextPage: false };
    try {
      return extension.getLatestUpdates(sourceId, page);
    } catch (e) {
      console.error("[Tachiyomi Worker] getLatestUpdates error:", e);
      return { mangas: [], hasNextPage: false };
    }
  },

  searchManga(sourceId: string, page: number, query: string): MangasPage {
    if (!extension) return { mangas: [], hasNextPage: false };
    try {
      return extension.searchManga(sourceId, page, query);
    } catch (e) {
      console.error("[Tachiyomi Worker] searchManga error:", e);
      return { mangas: [], hasNextPage: false };
    }
  },

  searchMangaWithFilters(sourceId: string, page: number, query: string, filterStateJson: string): MangasPage {
    if (!extension) return { mangas: [], hasNextPage: false };
    try {
      // Apply filters before searching
      if (filterStateJson && filterStateJson !== "[]") {
        const filterState = JSON.parse(filterStateJson) as FilterState[];
        extension.applyFilterState(sourceId, filterState);
      }
      return extension.searchManga(sourceId, page, query);
    } catch (e) {
      console.error("[Tachiyomi Worker] searchMangaWithFilters error:", e);
      return { mangas: [], hasNextPage: false };
    }
  },

  getMangaDetails(sourceId: string, mangaUrl: string): Manga | null {
    if (!extension) return null;
    try {
      return extension.getMangaDetails(sourceId, { url: mangaUrl });
    } catch (e) {
      console.error("[Tachiyomi Worker] getMangaDetails error:", e);
      return null;
    }
  },

  getChapterList(sourceId: string, mangaUrl: string): Chapter[] {
    if (!extension) return [];
    try {
      return extension.getChapterList(sourceId, { url: mangaUrl });
    } catch (e) {
      console.error("[Tachiyomi Worker] getChapterList error:", e);
      return [];
    }
  },

  getPageList(sourceId: string, chapterUrl: string): Page[] {
    if (!extension) return [];
    try {
      return extension.getPageList(sourceId, { url: chapterUrl });
    } catch (e) {
      console.error("[Tachiyomi Worker] getPageList error:", e);
      return [];
    }
  },

  fetchImage(sourceId: string, pageUrl: string, pageImageUrl: string): string {
    if (!extension) {
      throw new Error("Extension not loaded");
    }
    // Always returns base64 bytes (like Mihon's getImage)
    return extension.fetchImage(sourceId, pageUrl, pageImageUrl);
  },

  getHeaders(sourceId: string): Record<string, string> {
    if (!extension) {
      return {};
    }
    try {
      return extension.getHeaders(sourceId);
    } catch (e) {
      console.warn("[Tachiyomi Worker] getHeaders error:", e);
      return {};
    }
  },
};

Comlink.expose(workerApi);

/** Type alias for Comlink API */
export type WorkerApi = typeof workerApi;

