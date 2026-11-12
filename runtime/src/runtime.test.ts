import { afterEach, describe, expect, test } from "bun:test";
import { createRuntime, mangaInputJson } from "./runtime";
import { MangaStatus } from "./types";

const ok = (data: unknown) => JSON.stringify({ ok: true, data });

type Call = { fn: string; args: unknown[] };

/** A fake of what the compiled Kotlin exports, recording every call. */
function fakeExports(calls: Call[]) {
  const record =
    (fn: string, data: unknown) =>
    (...args: unknown[]) => {
      calls.push({ fn, args });
      return ok(data);
    };
  return {
    getManifest: record("getManifest", [{ id: "1", name: "Fake", lang: "en", baseUrl: "https://x", supportsLatest: true }]),
    getMangaDetails: record("getMangaDetails", { url: "/m/1", title: "Berserk", status: 1, genre: [], initialized: true }),
    getChapterList: record("getChapterList", []),
    getPageList: record("getPageList", []),
    getPopularManga: record("getPopularManga", { mangas: [], hasNextPage: false }),
    getLatestUpdates: record("getLatestUpdates", { mangas: [], hasNextPage: false }),
    searchManga: record("searchManga", { mangas: [], hasNextPage: false }),
    getFilterList: record("getFilterList", []),
    resetFilters: record("resetFilters", { ok: true }),
    applyFilterState: record("applyFilterState", { ok: true }),
    getHeaders: record("getHeaders", {}),
    fetchImage: record("fetchImage", ""),
  };
}

const g = globalThis as Record<string, unknown>;

function load(calls: Call[]) {
  g.__fakeExports = fakeExports(calls);
  const runtime = createRuntime({
    request: () => ({ status: 200, statusText: "OK", headers: {}, body: "" }),
  });
  return runtime.loadExtension("globalThis.__fakeExt = { tachiyomi: { generated: globalThis.__fakeExports } };");
}

afterEach(() => {
  delete g.__fakeExt;
  delete g.__fakeExports;
});

describe("runtime", () => {
  test("hands the library manga to getMangaDetails and getChapterList as a complete MangaDto", () => {
    const calls: Call[] = [];
    const ext = load(calls);
    ext.getSources();
    const manga = { url: "/m/1", title: "Berserk", thumbnailUrl: "https://x/c.jpg", status: MangaStatus.ONGOING, initialized: true };
    const details = ext.getMangaDetails("1", manga);
    ext.getChapterList("1", { url: "/m/1" });

    expect(details.title).toBe("Berserk");
    const [detailsCall, chaptersCall] = calls.filter((c) => c.fn !== "getManifest");
    expect(detailsCall.args[0]).toBe("1");
    expect(detailsCall.args[1]).toBe("/m/1");
    expect(JSON.parse(detailsCall.args[2] as string)).toEqual({
      url: "/m/1",
      title: "Berserk",
      artist: null,
      author: null,
      description: null,
      genre: [],
      status: 1,
      thumbnailUrl: "https://x/c.jpg",
      initialized: true,
    });
    // A bare url still produces every field the Kotlin decoder requires.
    expect(JSON.parse(chaptersCall.args[2] as string)).toEqual({
      url: "/m/1",
      title: "",
      artist: null,
      author: null,
      description: null,
      genre: [],
      status: 0,
      thumbnailUrl: null,
      initialized: false,
    });
  });

  test("mangaInputJson keeps every field it is given", () => {
    const json = JSON.parse(mangaInputJson({ url: "/m", title: "T", author: "A", artist: "B", description: "D", genre: ["x"], status: 2, thumbnailUrl: "u", initialized: true }));
    expect(json).toEqual({ url: "/m", title: "T", author: "A", artist: "B", description: "D", genre: ["x"], status: 2, thumbnailUrl: "u", initialized: true });
  });

  test("a Kotlin error result throws with its message", () => {
    const calls: Call[] = [];
    const ext = load(calls);
    (g.__fakeExports as Record<string, unknown>).getPageList = () =>
      JSON.stringify({ ok: false, error: { type: "Exception", message: "boom" } });
    expect(() => ext.getPageList("1", { url: "/c/1" })).toThrow(/boom/);
  });
});
