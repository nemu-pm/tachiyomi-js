/**
 * Async runtime types for Tachiyomi sources
 */
import type {
  Manga,
  Chapter,
  Page,
  MangasPage,
  SourceInfo,
  ExtensionManifest,
  FilterState,
} from "../types";

/**
 * Async Tachiyomi source interface
 * All methods return Promises for use on main thread
 */
export interface AsyncTachiyomiSource {
  readonly sourceId: string;
  readonly sourceInfo: SourceInfo;
  readonly manifest: ExtensionManifest;

  // Filter methods
  getFilterList(): Promise<FilterState[]>;
  resetFilters(): Promise<boolean>;
  applyFilterState(filterStateJson: string): Promise<boolean>;

  // Browse methods
  getPopularManga(page: number): Promise<MangasPage>;
  getLatestUpdates(page: number): Promise<MangasPage>;

  // Search methods
  searchManga(page: number, query: string): Promise<MangasPage>;
  searchMangaWithFilters(page: number, query: string, filterStateJson: string): Promise<MangasPage>;

  // Content methods
  getMangaDetails(mangaUrl: string): Promise<Manga | null>;
  getChapterList(mangaUrl: string): Promise<Chapter[]>;
  getPageList(chapterUrl: string): Promise<Page[]>;
  fetchImage(pageUrl: string, pageImageUrl: string): Promise<string>;
  getHeaders(): Promise<Record<string, string>>;

  // Preferences methods
  initPreferences(prefsName: string, values: Record<string, unknown>): Promise<void>;
  flushPrefChanges(): Promise<Array<{ name: string; key: string; value: unknown }>>;
  getSettingsSchema(): Promise<string | null>;

  /** Terminate the source and release resources */
  dispose(): void;
}

/**
 * Options for loading an async source
 */
export interface AsyncLoadOptions {
  /**
   * Proxy URL base for CORS bypass (browser only)
   * The target URL will be appended (URL-encoded)
   * Example: "https://proxy.example.com/?url="
   */
  proxyUrl?: string;

  /**
   * Initial preferences values
   */
  preferences?: {
    name: string;
    values: Record<string, unknown>;
  };
}

/**
 * Result of loading an extension
 */
export interface LoadedExtension {
  manifest: ExtensionManifest;
  sources: SourceInfo[];
  
  /**
   * Get an async source by ID
   */
  getSource(sourceId: string): AsyncTachiyomiSource;
  
  /**
   * Dispose all sources and terminate worker
   */
  dispose(): void;
}

