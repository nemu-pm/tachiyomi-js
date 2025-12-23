import { buildCommand, buildRouteMap } from "@stricli/core";
import pc from "picocolors";
import { loadOutputConfig } from "../config";
import { loadExtension, unwrapResult, type MangasPage, type TachiyomiExports } from "../lib/extension-loader";
import { printHeader, printJson, printListItem } from "../lib/output";

function getExtensionAndSource(extensionId: string) {
  const config = loadOutputConfig();
  const { exports, sources } = loadExtension(config.output, extensionId);

  if (sources.length === 0) {
    throw new Error("No sources found in extension");
  }

  const source = sources[0];
  console.log(`Loaded: ${source.name} (${source.lang})\n`);

  return { exports, source, sourceId: source.id };
}

export const popular = buildCommand({
  docs: {
    brief: "Get popular manga",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
      ],
    },
    flags: {
      page: {
        kind: "parsed",
        brief: "Page number",
        parse: (s: string) => parseInt(s, 10),
        optional: true,
      },
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { page?: number; json?: boolean }, extensionId: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);
    const page = flags.page ?? 1;

    console.log(`Fetching popular manga (page ${page})...`);
    const result = unwrapResult<MangasPage>(exports.getPopularManga(sourceId, page));

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader(`Popular Manga (${result.mangas?.length ?? 0} items, hasNextPage: ${result.hasNextPage})`);
    for (const m of result.mangas?.slice(0, 10) ?? []) {
      printListItem(m.title);
      console.log(`    URL: ${m.url}`);
      if (m.thumbnailUrl) console.log(`    Thumb: ${m.thumbnailUrl}`);
    }
    if ((result.mangas?.length ?? 0) > 10) {
      console.log(`\n... and ${result.mangas.length - 10} more`);
    }
  },
});

export const latest = buildCommand({
  docs: {
    brief: "Get latest manga updates",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
      ],
    },
    flags: {
      page: {
        kind: "parsed",
        brief: "Page number",
        parse: (s: string) => parseInt(s, 10),
        optional: true,
      },
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { page?: number; json?: boolean }, extensionId: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);
    const page = flags.page ?? 1;

    console.log(`Fetching latest updates (page ${page})...`);
    const result = unwrapResult<MangasPage>(exports.getLatestUpdates(sourceId, page));

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader(`Latest Updates (${result.mangas?.length ?? 0} items, hasNextPage: ${result.hasNextPage})`);
    for (const m of result.mangas?.slice(0, 10) ?? []) {
      printListItem(m.title);
      console.log(`    URL: ${m.url}`);
    }
  },
});

export const search = buildCommand({
  docs: {
    brief: "Search for manga",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
        { brief: "Search query", parse: String, placeholder: "query" },
      ],
    },
    flags: {
      page: {
        kind: "parsed",
        brief: "Page number",
        parse: (s: string) => parseInt(s, 10),
        optional: true,
      },
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { page?: number; json?: boolean }, extensionId: string, query: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);
    const page = flags.page ?? 1;

    console.log(`Searching for "${query}"...`);
    const result = unwrapResult<MangasPage>(exports.searchManga(sourceId, page, query));

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader(`Search Results (${result.mangas?.length ?? 0} items)`);
    for (const m of result.mangas?.slice(0, 10) ?? []) {
      printListItem(m.title);
      console.log(`    URL: ${m.url}`);
    }
  },
});

export const details = buildCommand({
  docs: {
    brief: "Get manga details",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
        { brief: "Manga URL (relative)", parse: String, placeholder: "url" },
      ],
    },
    flags: {
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }, extensionId: string, url: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);

    console.log(`Fetching manga details...`);
    const result = unwrapResult<unknown>(exports.getMangaDetails(sourceId, url));

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader("Manga Details");
    console.log(JSON.stringify(result, null, 2));
  },
});

export const chapters = buildCommand({
  docs: {
    brief: "Get chapter list",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
        { brief: "Manga URL (relative)", parse: String, placeholder: "url" },
      ],
    },
    flags: {
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }, extensionId: string, url: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);

    console.log(`Fetching chapter list...`);
    const result = unwrapResult<Array<{ name: string; url: string }>>(
      exports.getChapterList(sourceId, url)
    );

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader(`Chapters (${result?.length ?? 0})`);
    for (const c of result?.slice(0, 10) ?? []) {
      printListItem(c.name);
      console.log(`    URL: ${c.url}`);
    }
    if ((result?.length ?? 0) > 10) {
      console.log(`\n... and ${result.length - 10} more`);
    }
  },
});

export const pages = buildCommand({
  docs: {
    brief: "Get page list for a chapter",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
        { brief: "Chapter URL (relative)", parse: String, placeholder: "url" },
      ],
    },
    flags: {
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }, extensionId: string, url: string) => {
    const { exports, sourceId } = getExtensionAndSource(extensionId);

    console.log(`Fetching page list...`);
    const result = unwrapResult<Array<{ index: number; imageUrl?: string; url?: string }>>(
      exports.getPageList(sourceId, url)
    );

    if (flags.json) {
      printJson(result);
      return;
    }

    printHeader(`Pages (${result?.length ?? 0})`);
    for (const p of result?.slice(0, 5) ?? []) {
      console.log(`Page ${p.index + 1}: ${p.imageUrl || p.url}`);
    }
    if ((result?.length ?? 0) > 5) {
      console.log(`\n... and ${result.length - 5} more`);
    }
  },
});

// Image magic bytes for validation
const IMAGE_SIGNATURES: Array<{ name: string; bytes: number[] }> = [
  { name: "PNG", bytes: [0x89, 0x50, 0x4e, 0x47] },
  { name: "JPEG", bytes: [0xff, 0xd8, 0xff] },
  { name: "GIF", bytes: [0x47, 0x49, 0x46, 0x38] },
  { name: "WebP", bytes: [0x52, 0x49, 0x46, 0x46] }, // RIFF header
];

function detectImageFormat(buffer: Buffer): string | null {
  for (const sig of IMAGE_SIGNATURES) {
    if (sig.bytes.every((b, i) => buffer[i] === b)) {
      return sig.name;
    }
  }
  return null;
}

interface TestResult {
  test: string;
  passed: boolean;
  error?: string;
  data?: unknown;
}

export const all = buildCommand({
  docs: {
    brief: "Run all API tests on an extension",
    fullDescription: "Comprehensive test that validates popular, latest, search, details, chapters, pages, and image download. Useful for CI/CD.",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        { brief: "Extension path (e.g., en/mangapill)", parse: String, placeholder: "extension" },
      ],
    },
    flags: {
      json: {
        kind: "boolean",
        brief: "Output results as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }, extensionId: string) => {
    const config = loadOutputConfig();
    const { exports, sources } = loadExtension(config.output, extensionId);

    if (sources.length === 0) {
      throw new Error("No sources found in extension");
    }

    const source = sources[0];
    const sourceId = source.id;
    const results: TestResult[] = [];
    const log = flags.json ? () => {} : console.log;

    log(pc.cyan(`\n🧪 Testing: ${source.name} (${source.lang})\n`));

    // Collect manga samples from popular and latest
    const mangaSamples: Array<{ title: string; url: string }> = [];

    // Test 1: Popular
    try {
      log(pc.dim("Testing popular..."));
      const popular = unwrapResult<MangasPage>(exports.getPopularManga(sourceId, 1));
      const count = popular.mangas?.length ?? 0;
      if (count === 0) throw new Error("No manga returned");
      // Collect up to 5 samples
      mangaSamples.push(...popular.mangas.slice(0, 5));
      results.push({ test: "popular", passed: true, data: { count, hasNextPage: popular.hasNextPage } });
      log(pc.green(`  ✓ popular: ${count} manga`));
    } catch (e) {
      results.push({ test: "popular", passed: false, error: String(e) });
      log(pc.red(`  ✗ popular: ${e}`));
    }

    // Test 2: Latest
    try {
      log(pc.dim("Testing latest..."));
      const latest = unwrapResult<MangasPage>(exports.getLatestUpdates(sourceId, 1));
      const count = latest.mangas?.length ?? 0;
      if (count === 0) throw new Error("No manga returned");
      // Add more samples if needed
      if (mangaSamples.length < 5) {
        mangaSamples.push(...latest.mangas.slice(0, 5 - mangaSamples.length));
      }
      results.push({ test: "latest", passed: true, data: { count, hasNextPage: latest.hasNextPage } });
      log(pc.green(`  ✓ latest: ${count} manga`));
    } catch (e) {
      results.push({ test: "latest", passed: false, error: String(e) });
      log(pc.red(`  ✗ latest: ${e}`));
    }

    // Test 3: Search (using exact title from samples)
    if (mangaSamples.length > 0) {
      try {
        // Use a known manga title for more reliable search
        const searchManga = mangaSamples[0];
        const query = searchManga.title;
        log(pc.dim(`Testing search ("${query.slice(0, 30)}${query.length > 30 ? "..." : ""}")...`));
        const search = unwrapResult<MangasPage>(exports.searchManga(sourceId, 1, query));
        const count = search.mangas?.length ?? 0;
        // Check if we found the manga we searched for
        const found = search.mangas?.some(m => 
          m.title.toLowerCase().includes(searchManga.title.toLowerCase().slice(0, 10)) ||
          m.url === searchManga.url
        );
        results.push({ test: "search", passed: true, data: { query, count, foundTarget: found } });
        log(pc.green(`  ✓ search: ${count} results${found ? " (target found)" : ""}`));
      } catch (e) {
        results.push({ test: "search", passed: false, error: String(e) });
        log(pc.red(`  ✗ search: ${e}`));
      }
    }

    // Test 4: Manga Details (try up to 3 manga)
    let detailsManga: { title: string; url: string } | null = null;
    for (const manga of mangaSamples.slice(0, 3)) {
      try {
        log(pc.dim(`Testing details (${manga.title.slice(0, 30)})...`));
        const details = unwrapResult<{ title?: string; url?: string }>(
          exports.getMangaDetails(sourceId, manga.url)
        );
        detailsManga = manga;
        results.push({ test: "details", passed: true, data: { title: details.title || manga.title } });
        log(pc.green(`  ✓ details: ${details.title || manga.title}`));
        break;
      } catch (e) {
        log(pc.dim(`    (${manga.title.slice(0, 20)} failed, trying next...)`));
      }
    }
    if (!detailsManga) {
      results.push({ test: "details", passed: false, error: "All manga samples failed" });
      log(pc.red(`  ✗ details: All ${Math.min(3, mangaSamples.length)} samples failed`));
    }

    // Test 5: Chapters (try up to 3 manga)
    let chaptersData: { manga: { title: string; url: string }; chapters: Array<{ name: string; url: string }> } | null = null;
    for (const manga of mangaSamples.slice(0, 3)) {
      try {
        log(pc.dim(`Testing chapters (${manga.title.slice(0, 30)})...`));
        const chapters = unwrapResult<Array<{ name: string; url: string }>>(
          exports.getChapterList(sourceId, manga.url)
        );
        if (chapters?.length > 0) {
          chaptersData = { manga, chapters };
          results.push({ test: "chapters", passed: true, data: { manga: manga.title, count: chapters.length } });
          log(pc.green(`  ✓ chapters: ${chapters.length} chapters`));
          break;
        }
        log(pc.dim(`    (${manga.title.slice(0, 20)} has no chapters, trying next...)`));
      } catch (e) {
        log(pc.dim(`    (${manga.title.slice(0, 20)} failed, trying next...)`));
      }
    }
    if (!chaptersData) {
      results.push({ test: "chapters", passed: false, error: "No manga with chapters found" });
      log(pc.red(`  ✗ chapters: No manga with chapters found in ${Math.min(3, mangaSamples.length)} samples`));
    }

    // Test 6: Pages (try up to 3 chapters)
    let samplePages: Array<{ index: number; imageUrl?: string; url?: string }> = [];
    if (chaptersData) {
      for (const chapter of chaptersData.chapters.slice(0, 3)) {
        try {
          log(pc.dim(`Testing pages (${chapter.name.slice(0, 30)})...`));
          const pages = unwrapResult<Array<{ index: number; imageUrl?: string; url?: string }>>(
            exports.getPageList(sourceId, chapter.url)
          );
          if (pages?.length > 0) {
            samplePages = pages.slice(0, 3);
            results.push({ test: "pages", passed: true, data: { chapter: chapter.name, count: pages.length } });
            log(pc.green(`  ✓ pages: ${pages.length} pages`));
            break;
          }
          log(pc.dim(`    (${chapter.name.slice(0, 20)} has no pages, trying next...)`));
        } catch (e) {
          log(pc.dim(`    (${chapter.name.slice(0, 20)} failed, trying next...)`));
        }
      }
      if (samplePages.length === 0) {
        results.push({ test: "pages", passed: false, error: "No chapter with pages found" });
        log(pc.red(`  ✗ pages: No chapter with pages found in 3 samples`));
      }
    }

    // Test 7: Image Download (up to 3 pages)
    if (samplePages.length > 0) {
      log(pc.dim("Testing image download..."));
      let imagesPassed = 0;
      const imageResults: Array<{ page: number; format: string | null; size: number }> = [];

      for (const page of samplePages) {
        const imageUrl = page.imageUrl || "";
        if (!imageUrl) continue;

        try {
          const base64 = unwrapResult<string>(
            exports.fetchImage(sourceId, page.url || "", imageUrl)
          );
          const buffer = Buffer.from(base64, "base64");
          const format = detectImageFormat(buffer);

          if (format) {
            imagesPassed++;
            imageResults.push({ page: page.index + 1, format, size: buffer.length });
            log(pc.green(`    ✓ page ${page.index + 1}: ${format} (${(buffer.length / 1024).toFixed(1)}KB)`));
          } else {
            imageResults.push({ page: page.index + 1, format: null, size: buffer.length });
            log(pc.yellow(`    ⚠ page ${page.index + 1}: unknown format (${(buffer.length / 1024).toFixed(1)}KB)`));
          }
        } catch (e) {
          imageResults.push({ page: page.index + 1, format: null, size: 0 });
          log(pc.red(`    ✗ page ${page.index + 1}: ${e}`));
        }
      }

      results.push({
        test: "images",
        passed: imagesPassed > 0,
        data: { tested: samplePages.length, valid: imagesPassed, results: imageResults },
      });
    }

    // Summary
    const passed = results.filter((r) => r.passed).length;
    const failed = results.filter((r) => !r.passed).length;

    if (flags.json) {
      printJson({
        extension: extensionId,
        source: { id: sourceId, name: source.name, lang: source.lang },
        summary: { passed, failed, total: results.length },
        results,
      });
    } else {
      log("");
      if (failed === 0) {
        log(pc.green(`✓ All ${passed} tests passed`));
      } else {
        log(pc.yellow(`⚠ ${passed}/${passed + failed} tests passed`));
      }
    }

    // Exit with error code if tests failed
    if (failed > 0) {
      process.exit(1);
    }
  },
});

// Route map for test subcommands
export const testRoutes = buildRouteMap({
  routes: {
    all,
    popular,
    latest,
    search,
    details,
    chapters,
    pages,
  },
  docs: {
    brief: "Test extension API endpoints",
  },
});
