/**
 * Node.js/Bun async runtime
 *
 * No Worker needed - can use sync HTTP directly in Node.js.
 * All methods are still async for API consistency.
 */
import { createRuntime, type ExtensionInstance } from "../runtime";
import { createSyncNodeBridge } from "../http/sync-node";
import { installPrefsBridge, initPreferences, flushPrefChanges } from "../prefs-bridge";
import { registerImageCodec } from "../image-codec";
import type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";
import type { ExtensionManifest, SourceInfo, FilterState } from "../types";

// Re-export types
export type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";

// The shim reaches both through globals; install them before any extension code runs.
installPrefsBridge();
registerImageCodec();

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
    initPreferences(preferences.name, preferences.values);
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
        async getMangaDetails(manga) {
          try {
            return extension.getMangaDetails(sourceId, typeof manga === "string" ? { url: manga } : manga);
          } catch (e) {
            console.error("[Tachiyomi Node] getMangaDetails error:", e);
            return null;
          }
        },

        async getChapterList(manga) {
          try {
            return extension.getChapterList(sourceId, typeof manga === "string" ? { url: manga } : manga);
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
          initPreferences(prefsName, values);
        },

        async flushPrefChanges() {
          return flushPrefChanges();
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
