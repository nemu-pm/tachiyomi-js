/**
 * Node.js/Bun async runtime
 * 
 * No Worker needed - can use sync HTTP directly in Node.js.
 * All methods are still async for API consistency.
 */
import { createRuntime, type ExtensionInstance } from "../runtime";
import { createSyncNodeBridge } from "../http/sync-node";
import type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";
import type { ExtensionManifest, SourceInfo, FilterState } from "../types";

// Re-export types
export type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";

// ============ Preferences Storage ============
// SharedPreferences implementation for Kotlin/JS (Node.js version)

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

/**
 * Load a Tachiyomi extension asynchronously (Node.js/Bun version)
 * 
 * No Worker needed - sync HTTP works directly in Node.js.
 * 
 * @param jsUrl - URL to the compiled extension JavaScript
 * @param manifest - Extension manifest
 * @param options - Proxy URL and preferences configuration
 */
export async function loadExtension(
  jsUrl: string,
  manifest: ExtensionManifest,
  options: AsyncLoadOptions = {}
): Promise<LoadedExtension> {
  const { proxyUrl, preferences } = options;

  // Initialize preferences if provided
  if (preferences) {
    const prefs = new Map<string, unknown>();
    for (const [key, value] of Object.entries(preferences.values)) {
      prefs.set(key, value);
    }
    prefsStorage.set(preferences.name, prefs);
  }

  // Create HTTP bridge
  const httpBridge = createSyncNodeBridge({ proxyUrl });
  const runtime = createRuntime(httpBridge);

  // Fetch extension code
  const response = await fetch(jsUrl);
  if (!response.ok) {
    throw new Error(`Failed to fetch extension: ${response.status} ${response.statusText}`);
  }
  const code = await response.text();

  // Load extension
  const extension = runtime.loadExtension(code);
  const sources = extension.getSources() as SourceInfo[];
  
  // Update manifest with sources
  const updatedManifest: ExtensionManifest = {
    ...manifest,
    sources,
  };

  console.log("[Tachiyomi Node] Loaded, sources:", sources.length, sources.map(s => s.name).slice(0, 5));

  return createLoadedExtension(extension, updatedManifest);
}

function createLoadedExtension(
  extension: ExtensionInstance,
  manifest: ExtensionManifest
): LoadedExtension {
  const sources = manifest.sources ?? [];

  return {
    manifest,
    sources,

    getSource(sourceId: string): AsyncTachiyomiSource {
      const sourceInfo = sources.find(s => s.id === sourceId);
      if (!sourceInfo) {
        throw new Error(`Source not found: ${sourceId} in ${manifest.name}`);
      }

      return {
        sourceId,
        sourceInfo,
        manifest,

        // Filter methods
        async getFilterList() {
          try {
            return extension.getFilterList(sourceId);
          } catch (e) {
            console.error("[Tachiyomi Node] getFilterList error:", e);
            return [];
          }
        },

        async resetFilters() {
          try {
            extension.resetFilters(sourceId);
            return true;
          } catch (e) {
            console.error("[Tachiyomi Node] resetFilters error:", e);
            return false;
          }
        },

        async applyFilterState(filterStateJson) {
          try {
            const filterState = JSON.parse(filterStateJson) as FilterState[];
            extension.applyFilterState(sourceId, filterState);
            return true;
          } catch (e) {
            console.error("[Tachiyomi Node] applyFilterState error:", e);
            return false;
          }
        },

        // Browse methods
        async getPopularManga(page) {
          try {
            return extension.getPopularManga(sourceId, page);
          } catch (e) {
            console.error("[Tachiyomi Node] getPopularManga error:", e);
            return { mangas: [], hasNextPage: false };
          }
        },

        async getLatestUpdates(page) {
          try {
            return extension.getLatestUpdates(sourceId, page);
          } catch (e) {
            console.error("[Tachiyomi Node] getLatestUpdates error:", e);
            return { mangas: [], hasNextPage: false };
          }
        },

        // Search methods
        async searchManga(page, query) {
          try {
            return extension.searchManga(sourceId, page, query);
          } catch (e) {
            console.error("[Tachiyomi Node] searchManga error:", e);
            return { mangas: [], hasNextPage: false };
          }
        },

        async searchMangaWithFilters(page, query, filterStateJson) {
          try {
            if (filterStateJson && filterStateJson !== "[]") {
              const filterState = JSON.parse(filterStateJson) as FilterState[];
              extension.applyFilterState(sourceId, filterState);
            }
            return extension.searchManga(sourceId, page, query);
          } catch (e) {
            console.error("[Tachiyomi Node] searchMangaWithFilters error:", e);
            return { mangas: [], hasNextPage: false };
          }
        },

        // Content methods
        async getMangaDetails(mangaUrl) {
          try {
            return extension.getMangaDetails(sourceId, { url: mangaUrl });
          } catch (e) {
            console.error("[Tachiyomi Node] getMangaDetails error:", e);
            return null;
          }
        },

        async getChapterList(mangaUrl) {
          try {
            return extension.getChapterList(sourceId, { url: mangaUrl });
          } catch (e) {
            console.error("[Tachiyomi Node] getChapterList error:", e);
            return [];
          }
        },

        async getPageList(chapterUrl) {
          try {
            return extension.getPageList(sourceId, { url: chapterUrl });
          } catch (e) {
            console.error("[Tachiyomi Node] getPageList error:", e);
            return [];
          }
        },

        async fetchImage(pageUrl, pageImageUrl) {
          // Always returns base64 bytes (like Mihon's getImage)
          return extension.fetchImage(sourceId, pageUrl, pageImageUrl);
        },

        async getHeaders() {
          try {
            return extension.getHeaders(sourceId);
          } catch (e) {
            console.warn("[Tachiyomi Node] getHeaders error:", e);
            return {};
          }
        },

        // Preferences methods
        async initPreferences(prefsName, values) {
          const prefs = new Map<string, unknown>();
          for (const [key, value] of Object.entries(values)) {
            prefs.set(key, value);
          }
          prefsStorage.set(prefsName, prefs);
        },

        async flushPrefChanges() {
          const changes = pendingPrefChanges;
          pendingPrefChanges = [];
          return changes;
        },

        async getSettingsSchema() {
          try {
            const schema = extension.getSettingsSchema(sourceId);
            return schema ? JSON.stringify(schema) : null;
          } catch (e) {
            console.error("[Tachiyomi Node] getSettingsSchema error:", e);
            return null;
          }
        },

        dispose() {
          // No-op in Node.js - no worker to terminate
        },
      };
    },

    dispose() {
      // No-op in Node.js - no worker to terminate
    },
  };
}

