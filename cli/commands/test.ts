import { buildCommand, buildRouteMap } from "@stricli/core";
import { loadOutputConfig } from "../config";
import { loadExtension, unwrapResult, type MangasPage } from "../lib/extension-loader";
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
    const result = unwrapResult<unknown>(exports.getMangaDetails(sourceId, JSON.stringify({ url })));

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
      exports.getChapterList(sourceId, JSON.stringify({ url }))
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
      exports.getPageList(sourceId, JSON.stringify({ url }))
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

// Route map for test subcommands
export const testRoutes = buildRouteMap({
  routes: {
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
