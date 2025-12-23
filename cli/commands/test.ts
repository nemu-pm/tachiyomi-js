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

    // Test 1: Popular
    let sampleManga: { title: string; url: string } | null = null;
    try {
      log(pc.dim("Testing popular..."));
      const popular = unwrapResult<MangasPage>(exports.getPopularManga(sourceId, 1));
      const count = popular.mangas?.length ?? 0;
      if (count === 0) throw new Error("No manga returned");
      sampleManga = popular.mangas[0];
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
      if (!sampleManga) sampleManga = latest.mangas[0];
      results.push({ test: "latest", passed: true, data: { count, hasNextPage: latest.hasNextPage } });
      log(pc.green(`  ✓ latest: ${count} manga`));
    } catch (e) {
      results.push({ test: "latest", passed: false, error: String(e) });
      log(pc.red(`  ✗ latest: ${e}`));
    }

    // Test 3: Search (using first word of sample manga title)
    if (sampleManga) {
      try {
        const query = sampleManga.title.split(/\s+/)[0].slice(0, 10);
        log(pc.dim(`Testing search ("${query}")...`));
        const search = unwrapResult<MangasPage>(exports.searchManga(sourceId, 1, query));
        const count = search.mangas?.length ?? 0;
        results.push({ test: "search", passed: true, data: { query, count } });
        log(pc.green(`  ✓ search: ${count} results for "${query}"`));
      } catch (e) {
        results.push({ test: "search", passed: false, error: String(e) });
        log(pc.red(`  ✗ search: ${e}`));
      }
    }

    // Test 4: Manga Details
    let detailsOk = false;
    if (sampleManga) {
      try {
        log(pc.dim("Testing details..."));
        const details = unwrapResult<{ title?: string; url?: string }>(
          exports.getMangaDetails(sourceId, sampleManga.url)
        );
        detailsOk = true;
        results.push({ test: "details", passed: true, data: { title: details.title || sampleManga.title } });
        log(pc.green(`  ✓ details: ${details.title || sampleManga.title}`));
      } catch (e) {
        results.push({ test: "details", passed: false, error: String(e) });
        log(pc.red(`  ✗ details: ${e}`));
      }
    }

    // Test 5: Chapters
    let sampleChapter: { name: string; url: string } | null = null;
    if (sampleManga && detailsOk) {
      try {
        log(pc.dim("Testing chapters..."));
        const chapters = unwrapResult<Array<{ name: string; url: string }>>(
          exports.getChapterList(sourceId, sampleManga.url)
        );
        const count = chapters?.length ?? 0;
        if (count === 0) throw new Error("No chapters returned");
        sampleChapter = chapters[0];
        results.push({ test: "chapters", passed: true, data: { count } });
        log(pc.green(`  ✓ chapters: ${count} chapters`));
      } catch (e) {
        results.push({ test: "chapters", passed: false, error: String(e) });
        log(pc.red(`  ✗ chapters: ${e}`));
      }
    }

    // Test 6: Pages
    let samplePages: Array<{ index: number; imageUrl?: string; url?: string }> = [];
    if (sampleChapter) {
      try {
        log(pc.dim("Testing pages..."));
        const pages = unwrapResult<Array<{ index: number; imageUrl?: string; url?: string }>>(
          exports.getPageList(sourceId, sampleChapter.url)
        );
        const count = pages?.length ?? 0;
        if (count === 0) throw new Error("No pages returned");
        samplePages = pages.slice(0, 3);
        results.push({ test: "pages", passed: true, data: { count } });
        log(pc.green(`  ✓ pages: ${count} pages`));
      } catch (e) {
        results.push({ test: "pages", passed: false, error: String(e) });
        log(pc.red(`  ✗ pages: ${e}`));
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
