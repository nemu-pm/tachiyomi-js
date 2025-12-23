import * as fs from "fs";
import * as path from "path";
import { installHttpBridge } from "./http";

export interface TachiyomiExports {
  getManifest(): string;
  getPopularManga(sourceId: string, page: number): string;
  getLatestUpdates(sourceId: string, page: number): string;
  searchManga(sourceId: string, page: number, query: string): string;
  getMangaDetails(sourceId: string, mangaJson: string): string;
  getChapterList(sourceId: string, mangaJson: string): string;
  getPageList(sourceId: string, chapterJson: string): string;
  getFilterList(sourceId: string): string;
}

export interface SourceInfo {
  id: string;
  name: string;
  lang: string;
  baseUrl: string;
  supportsLatest: boolean;
}

export interface ExtensionManifest {
  name: string;
  pkg: string;
  version: string;
  nsfw: boolean;
  authors?: Array<{
    name?: string;
    github?: string;
    commits: number;
    firstCommit: string;
  }>;
}

export interface LoadedExtension {
  manifest: ExtensionManifest;
  exports: TachiyomiExports;
  sources: SourceInfo[];
}

export interface MangasPage {
  mangas: Array<{
    url: string;
    title: string;
    thumbnailUrl?: string;
    author?: string;
    artist?: string;
    description?: string;
    status?: number;
    genre?: string[];
  }>;
  hasNextPage: boolean;
}

/** Unwrap Kotlin/JS result format */
export function unwrapResult<T>(json: string): T {
  const result = JSON.parse(json) as { ok: boolean; data?: T; error?: any };
  if (!result.ok) {
    const errMsg = typeof result.error === "string" ? result.error : JSON.stringify(result.error, null, 2);
    throw new Error(errMsg || "Unknown error");
  }
  return result.data as T;
}

/** List available built extensions (returns lang/name format) */
export function listExtensions(outputDir: string): string[] {
  if (!fs.existsSync(outputDir)) {
    return [];
  }

  const extensions: string[] = [];

  // Iterate over lang directories
  for (const lang of fs.readdirSync(outputDir)) {
    const langDir = path.join(outputDir, lang);
    if (!fs.statSync(langDir).isDirectory()) continue;

    // Iterate over extension directories within each lang
    for (const name of fs.readdirSync(langDir)) {
      const extDir = path.join(langDir, name);
      const manifestPath = path.join(extDir, "manifest.json");
      if (fs.statSync(extDir).isDirectory() && fs.existsSync(manifestPath)) {
        extensions.push(`${lang}/${name}`);
      }
    }
  }

  return extensions;
}

/** Load and execute an extension */
export function loadExtension(outputDir: string, extensionId: string): LoadedExtension {
  // Install HTTP bridge before loading extension
  installHttpBridge();

  const extDir = path.join(outputDir, extensionId);
  const manifestPath = path.join(extDir, "manifest.json");
  const jsPath = path.join(extDir, "extension.js");

  if (!fs.existsSync(manifestPath)) {
    throw new Error(`Extension not found: ${extensionId}\nLooked in: ${extDir}`);
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf-8"));
  const code = fs.readFileSync(jsPath, "utf-8");

  // Execute the extension code
  const fn = new Function(code);
  fn();

  // Find the exports in globalThis
  const g = globalThis as any;
  let exports: TachiyomiExports | null = null;

  for (const key of Object.keys(g)) {
    if (g[key]?.tachiyomi?.generated) {
      exports = g[key].tachiyomi.generated;
      break;
    }
  }

  if (!exports || typeof exports.getManifest !== "function") {
    throw new Error("Could not find tachiyomi.generated exports");
  }

  // Get sources from extension
  const sourcesJson = exports.getManifest();
  const sources = unwrapResult<SourceInfo[]>(sourcesJson);

  return { manifest, exports, sources };
}

