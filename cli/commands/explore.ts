import { buildCommand } from "@stricli/core";
import { select, input, confirm } from "@inquirer/prompts";
import pc from "picocolors";
import * as fs from "fs";
import * as path from "path";
import { loadOutputConfig } from "../config";
import { listExtensionsWithInfo, loadExtension, unwrapResult, type MangasPage, type TachiyomiExports } from "../lib/extension-loader";

interface Manga {
  title: string;
  url: string;
  thumbnailUrl?: string;
  author?: string;
  artist?: string;
  description?: string;
  status?: number;
  genres?: string[];
}

interface Chapter {
  name: string;
  url: string;
  dateUpload?: number;
  chapterNumber?: number;
  scanlator?: string;
}

interface Page {
  index: number;
  imageUrl?: string;
  url?: string;
}

export const explore = buildCommand({
  docs: {
    brief: "Interactive mode to explore manga",
    fullDescription: "Browse manga interactively - select extensions, browse popular/latest, search, view details and chapters.",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [],
    },
    flags: {},
  },
  func: async () => {
    const config = loadOutputConfig();

    // List available extensions
    const extensions = listExtensionsWithInfo(config.output);
    if (extensions.length === 0) {
      console.log(pc.red("No extensions found. Build some first with: tachiyomi build <extension>"));
      return;
    }

    console.log(pc.cyan("\n🔍 Tachiyomi Extension Explorer\n"));

    // Select extension
    const extensionId = await select({
      message: "Select an extension",
      choices: extensions.map((ext) => ({
        name: `${ext.name} (${ext.lang})${ext.isNsfw ? " [NSFW]" : ""}`,
        value: ext.id,
      })),
    });

    const { exports, sources } = loadExtension(config.output, extensionId);
    if (sources.length === 0) {
      console.log(pc.red("No sources found in extension"));
      return;
    }

    const source = sources[0];
    const sourceId = source.id;
    console.log(pc.green(`\nLoaded: ${source.name}`));

    // Main loop
    while (true) {
      const action = await select({
        message: "What would you like to do?",
        choices: [
          { name: "🔥 Browse Popular", value: "popular" },
          { name: "🆕 Browse Latest", value: "latest" },
          { name: "🔎 Search", value: "search" },
          { name: "🚪 Exit", value: "exit" },
        ],
      });

      if (action === "exit") break;

      let mangas: Manga[] = [];
      let page = 1;
      let hasNextPage = true;

      if (action === "search") {
        const query = await input({ message: "Search query:" });
        if (!query.trim()) continue;

        console.log(pc.dim(`\nSearching for "${query}"...`));
        const result = unwrapResult<MangasPage>(exports.searchManga(sourceId, page, query));
        mangas = result.mangas ?? [];
        hasNextPage = result.hasNextPage ?? false;
      } else if (action === "popular") {
        console.log(pc.dim("\nFetching popular manga..."));
        const result = unwrapResult<MangasPage>(exports.getPopularManga(sourceId, page));
        mangas = result.mangas ?? [];
        hasNextPage = result.hasNextPage ?? false;
      } else if (action === "latest") {
        console.log(pc.dim("\nFetching latest updates..."));
        const result = unwrapResult<MangasPage>(exports.getLatestUpdates(sourceId, page));
        mangas = result.mangas ?? [];
        hasNextPage = result.hasNextPage ?? false;
      }

      if (mangas.length === 0) {
        console.log(pc.yellow("\nNo manga found."));
        continue;
      }

      // Manga selection loop
      while (true) {
        const choices = [
          ...mangas.map((m, i) => ({
            name: `${i + 1}. ${m.title}`,
            value: String(i),
          })),
          ...(hasNextPage ? [{ name: "📄 Load more", value: "more" }] : []),
          { name: "⬅ Back", value: "back" },
        ];

        const mangaChoice = await select({
          message: `Found ${mangas.length} manga:`,
          choices,
          pageSize: 15,
        });

        if (mangaChoice === "back") break;
        if (mangaChoice === "more") {
          page++;
          console.log(pc.dim(`\nLoading page ${page}...`));
          let result: MangasPage;
          if (action === "popular") {
            result = unwrapResult<MangasPage>(exports.getPopularManga(sourceId, page));
          } else if (action === "latest") {
            result = unwrapResult<MangasPage>(exports.getLatestUpdates(sourceId, page));
          } else {
            // search - would need to store query
            continue;
          }
          mangas = [...mangas, ...(result.mangas ?? [])];
          hasNextPage = result.hasNextPage ?? false;
          continue;
        }

        const manga = mangas[parseInt(mangaChoice)];
        await showMangaDetails(exports, sourceId, manga);
      }
    }

    console.log(pc.dim("\nBye! 👋"));
  },
});

async function showMangaDetails(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga
) {
  console.log(pc.dim("\nFetching details..."));

  const details = unwrapResult<Manga>(
    exports.getMangaDetails(sourceId, manga.url)
  );

  console.log("\n" + pc.bold(pc.cyan(details.title || manga.title)));
  if (details.author) console.log(pc.dim(`Author: ${details.author}`));
  if (details.artist && details.artist !== details.author) {
    console.log(pc.dim(`Artist: ${details.artist}`));
  }
  if (details.status !== undefined) {
    const statusMap: Record<number, string> = { 1: "Ongoing", 2: "Completed", 3: "Licensed", 4: "Publishing finished", 5: "Cancelled", 6: "On hiatus" };
    console.log(pc.dim(`Status: ${statusMap[details.status] || "Unknown"}`));
  }
  if (details.genres?.length) {
    console.log(pc.dim(`Genres: ${details.genres.join(", ")}`));
  }
  if (details.description) {
    const desc = details.description.replace(/<[^>]*>/g, "").slice(0, 300);
    console.log(pc.dim(`\n${desc}${details.description.length > 300 ? "..." : ""}`));
  }

  while (true) {
    const detailAction = await select({
      message: "What next?",
      choices: [
        { name: "📚 View Chapters", value: "chapters" },
        { name: "🔗 Show URL", value: "url" },
        { name: "📋 Raw JSON", value: "json" },
        { name: "⬅ Back", value: "back" },
      ],
    });

    if (detailAction === "back") break;

    if (detailAction === "url") {
      console.log(pc.cyan(`\nURL: ${manga.url}`));
      if (manga.thumbnailUrl) console.log(pc.cyan(`Thumbnail: ${manga.thumbnailUrl}`));
    } else if (detailAction === "json") {
      console.log("\n" + JSON.stringify(details, null, 2));
    } else if (detailAction === "chapters") {
      await showChapters(exports, sourceId, manga);
    }
  }
}

async function showChapters(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga
) {
  console.log(pc.dim("\nFetching chapters..."));

  const chapters = unwrapResult<Chapter[]>(
    exports.getChapterList(sourceId, manga.url)
  );

  if (!chapters?.length) {
    console.log(pc.yellow("No chapters found."));
    return;
  }

  console.log(pc.green(`\nFound ${chapters.length} chapters`));

  while (true) {
    const choices = [
      ...chapters.slice(0, 20).map((c, i) => ({
        name: `${i + 1}. ${c.name}`,
        value: String(i),
      })),
      ...(chapters.length > 20 ? [{ name: `... and ${chapters.length - 20} more`, value: "info" }] : []),
      { name: "⬅ Back", value: "back" },
    ];

    const chapterChoice = await select({
      message: "Select a chapter to view pages:",
      choices,
      pageSize: 15,
    });

    if (chapterChoice === "back") break;
    if (chapterChoice === "info") {
      console.log(pc.dim(`\nTotal: ${chapters.length} chapters`));
      console.log(pc.dim(`First: ${chapters[0].name}`));
      console.log(pc.dim(`Last: ${chapters[chapters.length - 1].name}`));
      continue;
    }

    const chapter = chapters[parseInt(chapterChoice)];
    await showPages(exports, sourceId, manga, chapter);
  }
}

async function showPages(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga,
  chapter: Chapter
) {
  console.log(pc.dim("\nFetching pages..."));

  const pages = unwrapResult<Page[]>(
    exports.getPageList(sourceId, chapter.url)
  );

  if (!pages?.length) {
    console.log(pc.yellow("No pages found."));
    return;
  }

  console.log(pc.green(`\n${chapter.name} - ${pages.length} pages`));

  while (true) {
    const choices = [
      { name: "📥 Download all pages", value: "download-all" },
      { name: "🖼️  Browse pages...", value: "browse" },
      { name: "📋 Show all URLs (JSON)", value: "json" },
      { name: "⬅ Back", value: "back" },
    ];

    const action = await select({
      message: `${pages.length} pages:`,
      choices,
    });

    if (action === "back") break;

    if (action === "json") {
      console.log("\n" + JSON.stringify(pages, null, 2));
    } else if (action === "download-all") {
      await downloadChapter(exports, sourceId, manga, chapter, pages);
    } else if (action === "browse") {
      await browsePagesMenu(exports, sourceId, manga, chapter, pages);
    }
  }
}

async function browsePagesMenu(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga,
  chapter: Chapter,
  pages: Page[]
) {
  while (true) {
    const choices = [
      ...pages.map((p) => ({
        name: `Page ${String(p.index + 1).padStart(3, "0")}: ${(p.imageUrl || p.url || "").slice(0, 60)}...`,
        value: String(p.index),
      })),
      { name: "⬅ Back", value: "back" },
    ];

    const pageChoice = await select({
      message: "Select a page:",
      choices,
      pageSize: 20,
    });

    if (pageChoice === "back") break;

    const page = pages[parseInt(pageChoice)];
    await showSinglePage(exports, sourceId, manga, chapter, page);
  }
}

async function showSinglePage(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga,
  chapter: Chapter,
  page: Page
) {
  const pageNum = String(page.index + 1).padStart(3, "0");
  const imageUrl = page.imageUrl || "";

  console.log(pc.cyan(`\nPage ${pageNum}`));
  console.log(pc.dim(`Image: ${imageUrl || "(no direct URL)"}`));

  const action = await select({
    message: "Options:",
    choices: [
      { name: "📥 Download this page", value: "download" },
      { name: "📋 Copy image URL", value: "copy" },
      { name: "⬅ Back", value: "back" },
    ],
  });

  if (action === "back") return;

  if (action === "copy") {
    if (imageUrl) {
      console.log(pc.green(`\n${imageUrl}`));
    } else {
      console.log(pc.yellow("\nNo direct image URL available"));
    }
  } else if (action === "download") {
    await downloadSinglePage(exports, sourceId, manga, chapter, page);
  }
  // Returns to page list after action
}

async function downloadSinglePage(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga,
  chapter: Chapter,
  page: Page
) {
  const pageNum = String(page.index + 1).padStart(3, "0");
  const imageUrl = page.imageUrl || page.url || "";

  if (!imageUrl) {
    console.log(pc.red("No image URL available"));
    return;
  }

  const mangaFolder = sanitizeFilename(manga.title);
  const chapterFolder = sanitizeFilename(chapter.name);
  const ext = imageUrl.match(/\.(jpe?g|png|gif|webp)/i)?.[1]?.toLowerCase() || "jpg";
  const defaultPath = path.join("downloads", mangaFolder, chapterFolder, `${pageNum}.${ext}`);

  const outputPath = await input({
    message: "Save as:",
    default: defaultPath,
  });

  // Create directory
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });

  console.log(pc.dim("\nDownloading..."));

  try {
    const base64 = unwrapResult<string>(
      exports.fetchImage(sourceId, page.url || "", imageUrl)
    );

    const buffer = Buffer.from(base64, "base64");
    fs.writeFileSync(outputPath, buffer);

    console.log(pc.green(`✓ Saved ${outputPath} (${(buffer.length / 1024).toFixed(1)}KB)`));
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    console.log(pc.red(`✗ Failed: ${msg}`));
  }
}

function sanitizeFilename(name: string): string {
  return name
    .replace(/[<>:"/\\|?*]/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);
}

async function downloadChapter(
  exports: TachiyomiExports,
  sourceId: string,
  manga: Manga,
  chapter: Chapter,
  pages: Page[]
) {
  const mangaFolder = sanitizeFilename(manga.title);
  const chapterFolder = sanitizeFilename(chapter.name);
  const defaultDir = path.join("downloads", mangaFolder, chapterFolder);

  const outputDir = await input({
    message: "Download directory:",
    default: defaultDir,
  });

  // Create directory
  fs.mkdirSync(outputDir, { recursive: true });

  console.log(pc.cyan(`\nDownloading ${pages.length} pages to ${outputDir}...\n`));

  let downloaded = 0;
  let failed = 0;

  for (const page of pages) {
    const pageNum = String(page.index + 1).padStart(3, "0");
    const imageUrl = page.imageUrl || page.url || "";

    if (!imageUrl) {
      console.log(pc.red(`Page ${pageNum}: No image URL`));
      failed++;
      continue;
    }

    try {
      process.stdout.write(pc.dim(`Page ${pageNum}...`));

      // Fetch image through extension (handles headers, interceptors)
      const base64 = unwrapResult<string>(
        exports.fetchImage(sourceId, page.url || "", imageUrl)
      );

      // Detect format from URL or default to jpg
      const ext = imageUrl.match(/\.(jpe?g|png|gif|webp)/i)?.[1]?.toLowerCase() || "jpg";
      const filename = `${pageNum}.${ext}`;
      const filepath = path.join(outputDir, filename);

      // Decode base64 and save
      const buffer = Buffer.from(base64, "base64");
      fs.writeFileSync(filepath, buffer);

      process.stdout.write(pc.green(` ✓ ${filename} (${(buffer.length / 1024).toFixed(1)}KB)\n`));
      downloaded++;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      process.stdout.write(pc.red(` ✗ ${msg.slice(0, 50)}\n`));
      failed++;
    }
  }

  console.log(pc.green(`\n✓ Downloaded ${downloaded}/${pages.length} pages`));
  if (failed > 0) {
    console.log(pc.yellow(`⚠ ${failed} pages failed`));
  }
  console.log(pc.dim(`Saved to: ${path.resolve(outputDir)}`));
}

