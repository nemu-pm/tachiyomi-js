import { buildCommand } from "@stricli/core";
import { select, input } from "@inquirer/prompts";
import pc from "picocolors";
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
    await showPages(exports, sourceId, chapter);
  }
}

async function showPages(
  exports: TachiyomiExports,
  sourceId: string,
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

  console.log(pc.green(`\n${chapter.name} - ${pages.length} pages\n`));

  for (const page of pages.slice(0, 5)) {
    console.log(pc.dim(`Page ${page.index + 1}: ${page.imageUrl || page.url}`));
  }
  if (pages.length > 5) {
    console.log(pc.dim(`... and ${pages.length - 5} more pages`));
  }

  await select({
    message: "Options:",
    choices: [
      { name: "📋 Show all URLs (JSON)", value: "json" },
      { name: "⬅ Back", value: "back" },
    ],
  }).then((action) => {
    if (action === "json") {
      console.log("\n" + JSON.stringify(pages, null, 2));
    }
  });
}

