/**
 * Core types for Tachiyomi extensions
 */

// ============================================================================
// Manga & Chapter Types
// ============================================================================

export enum MangaStatus {
  UNKNOWN = 0,
  ONGOING = 1,
  COMPLETED = 2,
  LICENSED = 3,
  PUBLISHING_FINISHED = 4,
  CANCELLED = 5,
  ON_HIATUS = 6,
}

export interface Manga {
  url: string;
  title: string;
  artist?: string;
  author?: string;
  description?: string;
  genre?: string[];
  status: MangaStatus;
  thumbnailUrl?: string;
  initialized: boolean;
}

export interface Chapter {
  url: string;
  name: string;
  dateUpload: number;
  chapterNumber: number;
  scanlator?: string;
}

export interface Page {
  index: number;
  url: string;
  imageUrl?: string;
}

export interface MangasPage {
  mangas: Manga[];
  hasNextPage: boolean;
}

// ============================================================================
// Source & Extension Types
// ============================================================================

export interface SourceInfo {
  id: string;
  name: string;
  lang: string;
  baseUrl: string;
  supportsLatest: boolean;
}

export interface ExtensionManifest {
  id: string;
  name: string;
  pkg: string;
  lang: string;
  version: number;
  nsfw: boolean;
  hasWebView?: boolean;
  hasCloudflare?: boolean;
  icon?: string;
  jsPath?: string;
  sources: SourceInfo[];
  authors?: Author[];
}

export interface Author {
  name: string;
  github?: string;
  commits: number;
  firstCommit: string;
}

// ============================================================================
// Filter Types (match Kotlin/JS output)
// ============================================================================

/** Header filter - section label, not interactive */
export interface FilterHeader {
  type: 'Header';
  name: string;
}

/** Separator filter - visual divider */
export interface FilterSeparator {
  type: 'Separator';
  name: string; // Usually empty
}

/** Text filter - free-form input */
export interface FilterText {
  type: 'Text';
  name: string;
  state: string;
}

/** Checkbox filter - boolean toggle */
export interface FilterCheckBox {
  type: 'CheckBox';
  name: string;
  state: boolean;
}

/** TriState filter - ignore/include/exclude */
export interface FilterTriState {
  type: 'TriState';
  name: string;
  state: number; // 0=ignore, 1=include, 2=exclude
}

/** Sort filter - sorting options */
export interface FilterSort {
  type: 'Sort';
  name: string;
  state: { index: number; ascending: boolean } | null;
  values: string[];
}

/** Select filter - dropdown/single select */
export interface FilterSelect {
  type: 'Select';
  name: string;
  state: number;
  values: string[];
}

/** Group filter - container for child filters */
export interface FilterGroup {
  type: 'Group';
  name: string;
  state: FilterState[]; // Nested filters
}

/** Union of all filter types */
export type FilterState =
  | FilterHeader
  | FilterSeparator
  | FilterText
  | FilterCheckBox
  | FilterTriState
  | FilterSort
  | FilterSelect
  | FilterGroup;

// ============================================================================
// Filter State Helpers
// ============================================================================

/** Minimal state update format for applyFilterState() */
export interface FilterStateUpdate {
  index: number;
  state?: boolean | number | string | { index: number; ascending: boolean };
  filters?: FilterStateUpdate[]; // For Group filters
}

/** Convert a single filter to its state update format */
export function filterToStateUpdate(filter: FilterState, index: number): FilterStateUpdate | null {
  switch (filter.type) {
    case 'CheckBox':
      return { index, state: filter.state };
    case 'TriState':
      return { index, state: filter.state };
    case 'Text':
      return filter.state ? { index, state: filter.state } : null;
    case 'Select':
      return { index, state: filter.state };
    case 'Sort':
      return filter.state ? { index, state: filter.state } : null;
    case 'Group':
      const childUpdates = filter.state
        .map((f, i) => filterToStateUpdate(f, i))
        .filter((u): u is FilterStateUpdate => u !== null);
      return childUpdates.length > 0 ? { index, filters: childUpdates } : null;
    default:
      return null; // Header, Separator have no state
  }
}

/** Convert filter list to JSON string for applyFilterState() */
export function buildFilterStateJson(filters: FilterState[]): string {
  const updates = filters
    .map((f, i) => filterToStateUpdate(f, i))
    .filter((u): u is FilterStateUpdate => u !== null);
  return JSON.stringify(updates);
}

// ============================================================================
// Settings Types
// ============================================================================

export interface SettingsSchema {
  preferences: PreferenceSchema[];
}

export type PreferenceSchema =
  | { type: 'EditTextPreference'; key: string; title: string; summary?: string; default?: string }
  | { type: 'CheckBoxPreference'; key: string; title: string; summary?: string; default?: boolean }
  | { type: 'ListPreference'; key: string; title: string; entries: string[]; entryValues: string[]; default?: string }
  | { type: 'MultiSelectListPreference'; key: string; title: string; entries: string[]; entryValues: string[]; default?: string[] }
  | { type: 'SwitchPreferenceCompat'; key: string; title: string; summary?: string; default?: boolean };

