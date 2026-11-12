import { beforeEach, describe, expect, test } from "bun:test";
import {
  PREFS_BRIDGE_GLOBALS,
  flushPrefChanges,
  getAllPreferences,
  initPreferences,
  installPrefsBridge,
} from "./prefs-bridge";

type Bridge = {
  __prefs_get(name: string, key: string): unknown;
  __prefs_set(name: string, key: string, value: unknown): void;
  __prefs_remove(name: string, key: string): void;
  __prefs_clear(name: string): void;
  __prefs_getAll(name: string): Record<string, unknown>;
};
const g = globalThis as unknown as Bridge;

describe("prefs bridge", () => {
  beforeEach(() => {
    installPrefsBridge();
    initPreferences("src", {});
    flushPrefChanges();
  });

  test("installs every global the shim calls", () => {
    for (const name of PREFS_BRIDGE_GLOBALS) {
      expect(typeof (globalThis as Record<string, unknown>)[name]).toBe("function");
    }
  });

  test("seeded values read back and are not reported as changes", () => {
    initPreferences("src", { lang: "en", nsfw: true, count: 3 });
    expect(g.__prefs_get("src", "lang")).toBe("en");
    expect(g.__prefs_get("src", "nsfw")).toBe(true);
    expect(g.__prefs_get("src", "missing")).toBeUndefined();
    expect(g.__prefs_getAll("src")).toEqual({ lang: "en", nsfw: true, count: 3 });
    expect(flushPrefChanges()).toEqual([]);
  });

  test("writes are visible to reads and drained once by flush", () => {
    g.__prefs_set("src", "mirror", "https://b.example");
    g.__prefs_set("src", "quality", 90);
    expect(g.__prefs_get("src", "mirror")).toBe("https://b.example");
    expect(flushPrefChanges()).toEqual([
      { name: "src", key: "mirror", value: "https://b.example" },
      { name: "src", key: "quality", value: 90 },
    ]);
    expect(flushPrefChanges()).toEqual([]);
    expect(getAllPreferences("src")).toEqual({ mirror: "https://b.example", quality: 90 });
  });

  test("files are independent", () => {
    g.__prefs_set("a", "k", 1);
    g.__prefs_set("b", "k", 2);
    expect(g.__prefs_get("a", "k")).toBe(1);
    expect(g.__prefs_get("b", "k")).toBe(2);
  });

  test("a Kotlin Long object is stored as a number", () => {
    g.__prefs_set("src", "last", { low_1: 5, high_1: 0, toNumber: () => 1700000000000 });
    expect(g.__prefs_get("src", "last")).toBe(1700000000000);
    expect(flushPrefChanges()).toEqual([{ name: "src", key: "last", value: 1700000000000 }]);
  });

  test("string sets stay arrays", () => {
    g.__prefs_set("src", "langs", ["en", "ja"]);
    expect(g.__prefs_get("src", "langs")).toEqual(["en", "ja"]);
  });

  test("a null write, remove and clear report removals", () => {
    initPreferences("src", { a: 1, b: 2, c: 3 });
    g.__prefs_set("src", "a", null);
    g.__prefs_remove("src", "b");
    g.__prefs_remove("src", "never-set");
    g.__prefs_clear("src");
    expect(g.__prefs_getAll("src")).toEqual({});
    expect(flushPrefChanges()).toEqual([
      { name: "src", key: "a", value: null },
      { name: "src", key: "b", value: null },
      { name: "src", key: "c", value: null },
    ]);
  });
});
