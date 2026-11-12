/**
 * The shim talks to the host through globals it names in Kotlin. These tests read the Kotlin source
 * so a rename on either side fails here instead of silently falling back (prefs) or throwing inside
 * a source (images).
 */
import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { PREFS_BRIDGE_GLOBALS } from "./prefs-bridge";
import { IMAGE_CODEC_GLOBALS } from "./image-codec";

const shim = join(import.meta.dir, "../../shim/src/jsMain/kotlin");
const read = (rel: string) => readFileSync(join(shim, rel), "utf8");
const src = (rel: string) => readFileSync(join(import.meta.dir, rel), "utf8");

describe("shim contract", () => {
  test("the prefs bridge installs exactly the __prefs_* globals SharedPreferences.kt calls", () => {
    const kotlin = read("android/content/SharedPreferences.kt");
    const called = new Set(kotlin.match(/__prefs_\w+/g));
    expect([...called].sort()).toEqual([...PREFS_BRIDGE_GLOBALS].sort());
  });

  test("the image codec installs the globals ImageBridge.kt calls and answers in its field names", () => {
    const kotlin = read("android/graphics/ImageBridge.kt");
    const called = new Set(kotlin.match(/globalThis\.(tachiyomi\w+)/g)?.map((m) => m.slice("globalThis.".length)));
    expect([...called].sort()).toEqual([...IMAGE_CODEC_GLOBALS].sort());
    for (const field of ["width", "height", "pixelsBase64"]) {
      expect(kotlin).toContain(`result.${field}`);
    }
    expect(src("image-codec.ts")).toMatch(/pixelsBase64: string/);
  });

  test("every entry point installs both bridges before extension code can run", () => {
    for (const entry of ["index.ts", "index.node.ts", "async/worker.ts", "async/index.node.ts"]) {
      const code = src(entry);
      expect(code, entry).toMatch(/^registerImageCodec\(\);/m);
      expect(code, entry).toMatch(/^installPrefsBridge\(\);/m);
    }
  });
});
