/**
 * @tachiyomi-js/runtime - Node.js/Bun entry point
 * 
 * Uses the `canvas` package for image manipulation.
 * Install with: npm install canvas@next
 */

// Register node image codec on import
import { registerImageCodec } from './image-codec.node';
registerImageCodec();

// Re-export everything from the main module (types + runtime)
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

export { MangaStatus, filterToStateUpdate, buildFilterStateJson } from './types';

export type {
  HttpBridge,
  HttpRequest,
  HttpResponse,
} from './http';

export {
  TachiyomiRuntime,
  createRuntime,
  type ExtensionInstance,
} from './runtime';

// Also export async image functions (useful for testing in Node)
export { decodeImageAsync, encodeImageAsync } from './image-codec.node';
