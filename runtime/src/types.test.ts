import { describe, expect, test } from "bun:test";
import { buildFilterStateJson, filterToStateUpdate, type FilterState } from "./types";

/** As MainKtTemplate.serializeFilter emits them: lowercase type, group children under `filters`. */
const filters: FilterState[] = [
  { type: "header", name: "Note" },
  { type: "text", name: "Author", state: "" },
  { type: "checkbox", name: "Completed", state: true },
  { type: "tristate", name: "Action", state: 2 },
  { type: "select", name: "Type", state: 1, values: ["Any", "Manga", "Manhwa"] },
  { type: "sort", name: "Order", values: ["Popular", "Latest"], state: { index: 1, ascending: false } },
  { type: "sort", name: "Unset order", values: ["A", "B"] },
  { type: "separator", name: "" },
  {
    type: "group",
    name: "Genres",
    filters: [
      { type: "checkbox", name: "Comedy", state: false },
      { type: "checkbox", name: "Drama", state: true },
    ],
  },
  { type: "group", name: "Empty", filters: [{ type: "header", name: "x" }] },
];

describe("filter state", () => {
  test("buildFilterStateJson emits what applyFilterState parses", () => {
    expect(JSON.parse(buildFilterStateJson(filters))).toEqual([
      { index: 2, state: true },
      { index: 3, state: 2 },
      { index: 4, state: 1 },
      { index: 5, state: { index: 1, ascending: false } },
      {
        index: 8,
        filters: [
          { index: 0, state: false },
          { index: 1, state: true },
        ],
      },
    ]);
  });

  test("text filters only carry non-empty state", () => {
    expect(filterToStateUpdate({ type: "text", name: "Author", state: "Oda" }, 0)).toEqual({ index: 0, state: "Oda" });
    expect(filterToStateUpdate({ type: "text", name: "Author", state: "" }, 0)).toBeNull();
  });
});
