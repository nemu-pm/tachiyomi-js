/**
 * @tachiyomi-js/runtime - Node.js/Bun entry point
 *
 * Same as the browser entry: the image codec is pure JS, so no native package is needed.
 */

// The shim reaches the image codec and SharedPreferences through globals; install both on import.
import { registerImageCodec } from './image-codec';
import { installPrefsBridge } from './prefs-bridge';
registerImageCodec();
installPrefsBridge();

export { initPreferences, flushPrefChanges, getAllPreferences, type PrefChange } from './prefs-bridge';
export { decodeImage, encodeImage, decodeImageAsync, encodeImageAsync, type DecodedImage } from './image-codec';

// Re-export everything from the main module (types + runtime)
export type {
  Manga,
  MangaInput,
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
