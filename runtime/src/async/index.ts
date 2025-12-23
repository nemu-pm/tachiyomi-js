/**
 * Browser async runtime
 * 
 * Creates a Web Worker internally, exposes clean async API.
 * Consumer doesn't need to manage workers.
 */
import * as Comlink from "comlink";
import type { WorkerApi } from "./worker";
import type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";
import type { ExtensionManifest, SourceInfo } from "../types";

// Re-export types
export type { AsyncTachiyomiSource, AsyncLoadOptions, LoadedExtension } from "./types";

// Cache loaded extensions (one worker per jsUrl)
const loadedExtensions = new Map<string, {
  worker: Worker;
  workerApi: Comlink.Remote<WorkerApi>;
  manifest: ExtensionManifest;
}>();

/**
 * Load a Tachiyomi extension asynchronously (browser version)
 * 
 * Creates a Web Worker internally to handle sync HTTP.
 * Returns a LoadedExtension that can create sources.
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

  // Check cache
  const cached = loadedExtensions.get(jsUrl);
  if (cached) {
    return createLoadedExtension(cached.workerApi, cached.manifest, cached.worker, jsUrl);
  }

  // Create worker
  const worker = new Worker(
    new URL("./worker.js", import.meta.url),
    { type: "module" }
  );

  // Wrap with Comlink
  const workerApi = Comlink.wrap<WorkerApi>(worker);

  // Initialize preferences if provided
  if (preferences) {
    await workerApi.initPreferences(preferences.name, preferences.values);
  }

  // Get proxy URL string
  const proxyUrlString = proxyUrl ? "" : null; // We'll pass the function result
  
  // Load extension in worker
  const result = await workerApi.load(
    jsUrl,
    manifest,
    proxyUrl ? "PROXY_PLACEHOLDER" : null // Worker will handle proxy
  );

  if (!result.success || !result.manifest) {
    worker.terminate();
    throw new Error(`Failed to load Tachiyomi extension: ${manifest.name}`);
  }

  // Cache
  const ext = { worker, workerApi, manifest: result.manifest };
  loadedExtensions.set(jsUrl, ext);

  return createLoadedExtension(workerApi, result.manifest, worker, jsUrl);
}

function createLoadedExtension(
  workerApi: Comlink.Remote<WorkerApi>,
  manifest: ExtensionManifest,
  worker: Worker,
  jsUrl: string
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
          return workerApi.getFilterList(sourceId);
        },

        async resetFilters() {
          return workerApi.resetFilters(sourceId);
        },

        async applyFilterState(filterStateJson) {
          return workerApi.applyFilterState(sourceId, filterStateJson);
        },

        // Browse methods
        async getPopularManga(page) {
          return workerApi.getPopularManga(sourceId, page);
        },

        async getLatestUpdates(page) {
          return workerApi.getLatestUpdates(sourceId, page);
        },

        // Search methods
        async searchManga(page, query) {
          return workerApi.searchManga(sourceId, page, query);
        },

        async searchMangaWithFilters(page, query, filterStateJson) {
          return workerApi.searchMangaWithFilters(sourceId, page, query, filterStateJson);
        },

        // Content methods
        async getMangaDetails(mangaUrl) {
          return workerApi.getMangaDetails(sourceId, mangaUrl);
        },

        async getChapterList(mangaUrl) {
          return workerApi.getChapterList(sourceId, mangaUrl);
        },

        async getPageList(chapterUrl) {
          return workerApi.getPageList(sourceId, chapterUrl);
        },

        async fetchImage(pageUrl, pageImageUrl) {
          return workerApi.fetchImage(sourceId, pageUrl, pageImageUrl);
        },

        async getHeaders() {
          return workerApi.getHeaders(sourceId);
        },

        // Preferences methods
        async initPreferences(prefsName, values) {
          await workerApi.initPreferences(prefsName, values);
        },

        async flushPrefChanges() {
          return workerApi.flushPrefChanges();
        },

        async getSettingsSchema() {
          return workerApi.getSettingsSchema(sourceId);
        },

        dispose() {
          // Individual source disposal is a no-op
          // Use LoadedExtension.dispose() to terminate worker
        },
      };
    },

    dispose() {
      loadedExtensions.delete(jsUrl);
      worker.terminate();
    },
  };
}

