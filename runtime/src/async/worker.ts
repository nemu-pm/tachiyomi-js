/**
 * Web Worker for running Tachiyomi Kotlin/JS extension sources
 * Sync XHR works in workers, enabling blocking HTTP calls from Kotlin/JS
 */
import * as Comlink from "comlink";
import { createRuntime, type ExtensionInstance } from "../runtime";
import { createSyncXhrBridge } from "../http/sync-xhr";
import { installPrefsBridge, initPreferences, flushPrefChanges, type PrefChange } from "../prefs-bridge";
import { registerImageCodec } from "../image-codec";
import type {
  MangasPage,
  Manga,
  MangaInput,
  Chapter,
  Page,
  FilterState,
  SourceInfo,
  ExtensionManifest,
} from "../types";

// The shim reaches both through globals; install them before any extension code runs.
installPrefsBridge();
registerImageCodec();

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
    initPreferences(prefsName, values);
  },

  flushPrefChanges(): PrefChange[] {
    return flushPrefChanges();
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

  /** Pass the library row when you have it: sources read title, cover and author off it. */
  getMangaDetails(sourceId: string, manga: string | MangaInput): Manga | null {
    if (!extension) return null;
    try {
      return extension.getMangaDetails(sourceId, typeof manga === "string" ? { url: manga } : manga);
    } catch (e) {
      console.error("[Tachiyomi Worker] getMangaDetails error:", e);
      return null;
    }
  },

  getChapterList(sourceId: string, manga: string | MangaInput): Chapter[] {
    if (!extension) return [];
    try {
      return extension.getChapterList(sourceId, typeof manga === "string" ? { url: manga } : manga);
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
