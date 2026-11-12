/**
 * SharedPreferences storage for the Kotlin/JS shim.
 *
 * The shim's BridgedSharedPreferences (shim/src/jsMain/kotlin/android/content/SharedPreferences.kt)
 * reads and writes through five globals: `__prefs_get(name, key)`, `__prefs_set(name, key, value)`,
 * `__prefs_remove(name, key)`, `__prefs_clear(name)` and `__prefs_getAll(name)`. When they are missing
 * every source silently runs on an empty in-memory map and nothing a user configures survives a reload.
 *
 * The host seeds a preferences file with initPreferences and drains what sources wrote with
 * flushPrefChanges; a change with a null value means the key was removed.
 */

export interface PrefChange {
  name: string;
  key: string;
  /** null when the key was removed */
  value: unknown;
}

/** The globals the shim calls; the shim-contract test checks this list against the Kotlin source. */
export const PREFS_BRIDGE_GLOBALS = [
  "__prefs_get",
  "__prefs_set",
  "__prefs_remove",
  "__prefs_clear",
  "__prefs_getAll",
] as const;

const storage = new Map<string, Map<string, unknown>>();
let pending: PrefChange[] = [];

function file(name: string): Map<string, unknown> {
  let data = storage.get(name);
  if (!data) {
    data = new Map();
    storage.set(name, data);
  }
  return data;
}

/**
 * Values arrive straight from Kotlin: a Long is a Kotlin object with toNumber(), string sets are arrays,
 * a missing value is undefined.
 */
function normalize(value: unknown): unknown {
  if (value === undefined) return null;
  if (value !== null && typeof value === "object" && typeof (value as { toNumber?: unknown }).toNumber === "function") {
    return (value as { toNumber(): number }).toNumber();
  }
  return value;
}

const bridge = {
  __prefs_get(name: string, key: string): unknown {
    return storage.get(name)?.get(key);
  },
  __prefs_set(name: string, key: string, value: unknown): void {
    const normalized = normalize(value);
    if (normalized === null) {
      bridge.__prefs_remove(name, key);
      return;
    }
    file(name).set(key, normalized);
    pending.push({ name, key, value: normalized });
  },
  __prefs_remove(name: string, key: string): void {
    const data = storage.get(name);
    if (!data?.has(key)) return;
    data.delete(key);
    pending.push({ name, key, value: null });
  },
  __prefs_clear(name: string): void {
    const data = storage.get(name);
    if (!data) return;
    for (const key of data.keys()) pending.push({ name, key, value: null });
    data.clear();
  },
  __prefs_getAll(name: string): Record<string, unknown> {
    return Object.fromEntries(storage.get(name) ?? []);
  },
} satisfies Record<(typeof PREFS_BRIDGE_GLOBALS)[number], unknown>;

/** Install the `__prefs_*` globals. Safe to call more than once. */
export function installPrefsBridge(): void {
  Object.assign(globalThis, bridge);
}

/** Replace the contents of a preferences file with the host's persisted values. Not reported as changes. */
export function initPreferences(name: string, values: Record<string, unknown>): void {
  storage.set(name, new Map(Object.entries(values)));
}

/** Return and forget the writes sources made since the last call, in order. */
export function flushPrefChanges(): PrefChange[] {
  const changes = pending;
  pending = [];
  return changes;
}

/** Current contents of a preferences file. */
export function getAllPreferences(name: string): Record<string, unknown> {
  return bridge.__prefs_getAll(name);
}
