/**
 * @nemu.pm/tachiyomi-runtime
 * 
 * Runtime for loading and executing Tachiyomi extensions compiled to JavaScript.
 * 
 * @example
 * ```typescript
 * import { createRuntime, type Manga, type MangasPage } from '@tachiyomi-js/runtime';
 * 
 * // Create runtime with your HTTP implementation
 * const runtime = createRuntime({
 *   request(req, wantBytes) {
 *     // Implement sync HTTP (e.g., XMLHttpRequest in a Web Worker)
 *     return { status: 200, statusText: 'OK', headers: {}, body: '...' };
 *   }
 * });
 * 
 * // Load an extension
 * const ext = runtime.loadExtension(extensionJsCode);
 * 
 * // Get sources
 * const sources = ext.getSources();
 * console.log(sources[0].name); // "MangaDex"
 * 
 * // Fetch manga
 * const popular: MangasPage = ext.getPopularManga(sources[0].id, 1);
 * console.log(popular.mangas[0].title);
 * ```
 * 
 * @packageDocumentation
 */

// Register browser image codec on import
import { registerImageCodec } from './image-codec.browser';
registerImageCodec();

// Types
export type {
  Manga,
  Chapter,
  Page,
  MangasPage,
  SourceInfo,
  ExtensionManifest,
  Author,
  FilterState,
  FilterHeader,
  FilterSeparator,
  FilterText,
  FilterCheckBox,
  FilterTriState,
  FilterSort,
  FilterSelect,
  FilterGroup,
  FilterStateUpdate,
  SettingsSchema,
  PreferenceSchema,
} from './types';

// Enums and functions (exported as values)
export { MangaStatus, filterToStateUpdate, buildFilterStateJson } from './types';

// HTTP Bridge
export type {
  HttpBridge,
  HttpRequest,
  HttpResponse,
} from './http';

// Runtime
export {
  TachiyomiRuntime,
  createRuntime,
  type ExtensionInstance,
} from './runtime';
