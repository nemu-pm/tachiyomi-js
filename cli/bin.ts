#!/usr/bin/env bun
// @nemu.pm/tachiyomi-cli - Test and explore Tachiyomi extensions
import { run } from "@stricli/core";
import { app } from "./app";

await run(app, process.argv.slice(2), {
  process,
});

